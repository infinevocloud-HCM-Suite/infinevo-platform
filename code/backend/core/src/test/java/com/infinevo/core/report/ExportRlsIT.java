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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
        ReportTestSchema.insertEmployee(tenantA, "A-DELETED-1", "ACTIVE", true);
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
        assertThat(file)
                .contains("A-1")
                .contains("A-2")
                .doesNotContain("A-DELETED-1")
                .doesNotContain("B-SECRET-1");
    }

    @Test
    @DisplayName("Row-level security alone hides tenant B's report definitions from app_user bound to A")
    void policyHidesTheOtherTenant() throws Exception {
        UUID tenantA = ReportTestSchema.insertTenant("A " + UUID.randomUUID());
        UUID tenantB = ReportTestSchema.insertTenant("B " + UUID.randomUUID());
        UUID defA = ReportTestSchema.definitionId(tenantA, "employees");
        UUID defB = ReportTestSchema.definitionId(tenantB, "employees");

        assertThat(countAsAppUser(tenantA, "SELECT count(*) FROM core.report_definition WHERE id = ?", defA))
                .as("the control: A sees its own definition")
                .isEqualTo(1);
        assertThat(countAsAppUser(tenantA, "SELECT count(*) FROM core.report_definition WHERE id = ?", defB))
                .as("A must not see B's definition under RLS alone")
                .isZero();
        assertThat(countAsAppUser(tenantA, "SELECT count(*) FROM core.report_definition WHERE tenant_id = ?", tenantB))
                .as("A must not see any of B's definitions")
                .isZero();
        assertThat(countAsAppUser(tenantA, "SELECT count(*) FROM core.report_definition WHERE tenant_id = ?", tenantA))
                .as("the control: A sees its three seeded definitions")
                .isEqualTo(3);
    }

    private static long countAsAppUser(UUID boundTenant, String sql, UUID param) throws Exception {
        try (Connection conn = ReportTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                ReportTestSchema.bindTenant(conn, boundTenant);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, param);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
