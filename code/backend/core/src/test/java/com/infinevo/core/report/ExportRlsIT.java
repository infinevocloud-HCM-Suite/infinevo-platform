package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.security.PublicEndpoints;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W-23.1 spec section 7 — an export run by tenant A contains no tenant B row, including in the row
 * count. The export reads under the tenant the request is bound to, exactly as a screen does.
 */
@SpringBootTest(classes = ReportTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            ReportTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ExportRlsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("Tenant A's export holds A's employees only - B's are neither in the file nor in the count")
    void exportIsTenantScoped() throws Exception {
        UUID tenantA = ReportTestSchema.insertTenant("A " + UUID.randomUUID());
        UUID tenantB = ReportTestSchema.insertTenant("B " + UUID.randomUUID());
        ReportTestSchema.insertEmployee(tenantA, "A-1", "ACTIVE");
        ReportTestSchema.insertEmployee(tenantA, "A-2", "ACTIVE");
        ReportTestSchema.insertEmployee(tenantB, "B-SECRET-1", "ACTIVE");
        UUID adminA = UUID.randomUUID();
        ReportTestSchema.insertMember(tenantA, adminA, "tenant-admin");

        // A CSV definition, so the file can be read as text.
        String create = "{\"code\":\"staff-csv\",\"name\":\"Staff\",\"source\":\"employee\","
                + "\"columns\":[\"employee_number\",\"status\"],\"format\":\"CSV\","
                + "\"requiredAction\":\"core.employee.export\"}";
        String created = mvc.perform(post("/api/v1/report-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create)
                        .with(jwt().jwt(t -> t.subject(adminA.toString()).claim("tenant_id", tenantA.toString()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String definitionId = json.readTree(created).get("id").asText();

        String exported = mvc.perform(post("/api/v1/exports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + definitionId + "\"}")
                        .with(jwt().jwt(t -> t.subject(adminA.toString()).claim("tenant_id", tenantA.toString()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = json.readTree(exported);
        assertThat(response.get("rowCount").asLong()).isEqualTo(2);

        String url = response.get("url").asText();
        String file = new String(
                mvc.perform(get(PublicEndpoints.DOCUMENT_DOWNLOAD).param("t", url.substring(url.indexOf("?t=") + 3)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertThat(file).contains("A-1").contains("A-2").doesNotContain("B-SECRET-1");
    }
}
