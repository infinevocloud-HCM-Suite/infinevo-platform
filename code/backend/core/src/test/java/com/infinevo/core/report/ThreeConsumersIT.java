package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The build order's acceptance criterion, asserted directly — {@code 09-build-order.md:207}: "three
 * screens use one export path" (W-23.1 spec section 7).
 *
 * <p>The three are employees, the org masters and the audit log (decision D3), through the three
 * definitions {@code V040} seeds for every tenant — which also proves the seeded column names match the
 * sources' allow-lists. The spec's leave-balance and pay-input consumers arrive with {@code W-16.2}
 * and {@code W-19}, whose tables do not exist yet.
 */
@SpringBootTest(classes = ReportTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            ReportTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ThreeConsumersIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID admin;

    @BeforeEach
    void seed() throws Exception {
        tenant = ReportTestSchema.insertTenant("Three " + UUID.randomUUID());
        admin = UUID.randomUUID();
        ReportTestSchema.insertMember(tenant, admin, "tenant-admin");
        ReportTestSchema.insertEmployee(tenant, "E-1", "ACTIVE");
        ReportTestSchema.insertEmployee(tenant, "E-2", "TERMINATED");
        ReportTestSchema.insertDepartment(tenant, "ENG");
        ReportTestSchema.insertDepartment(tenant, "OPS");
        ReportTestSchema.insertAuditRow(tenant, "employee");
    }

    @Test
    @DisplayName("Employees, org masters and the audit log all export through the one service")
    void threeConsumersOnePath() throws Exception {
        assertThat(export("employees").get("rowCount").asLong()).isEqualTo(2);
        assertThat(export("org-masters").get("rowCount").asLong()).isEqualTo(2);
        assertThat(export("audit-log").get("rowCount").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("A request filter narrows a seeded definition - the source applies it in its query")
    void filtersReachTheSource() throws Exception {
        JsonNode active = export("employees", "{\"status\":\"ACTIVE\"}");
        assertThat(active.get("rowCount").asLong()).isEqualTo(1);
    }

    private JsonNode export(String code) throws Exception {
        return export(code, "null");
    }

    private JsonNode export(String code, String filters) throws Exception {
        String body = "{\"definitionId\":\"" + ReportTestSchema.definitionId(tenant, code) + "\",\"filters\":" + filters
                + "}";
        String response = mvc.perform(post("/api/v1/exports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(t -> t.subject(admin.toString()).claim("tenant_id", tenant.toString()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }
}
