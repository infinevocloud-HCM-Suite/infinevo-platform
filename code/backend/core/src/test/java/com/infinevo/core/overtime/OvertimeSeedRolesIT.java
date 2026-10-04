package com.infinevo.core.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-68 §7 — {@code V148} grants {@code core.overtime.read} to {@code hr} and {@code payroll-officer}
 * in a tenant that already exists, and nothing else moves; {@code list} carries {@code employee_name},
 * {@code get} does not.
 */
@SpringBootTest(classes = OvertimeTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            OvertimeTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class OvertimeSeedRolesIT extends AbstractIntegrationTest {

    private static final String HELD = "SELECT count(*) FROM core.role_action ra JOIN core.role r ON r.id = ra.role_id"
            + " WHERE ra.tenant_id = ? AND r.code = ? AND ra.action_code = ?";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OvertimeService overtimeService;

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private static boolean holds(UUID tenant, String role, String action) throws Exception {
        return OvertimeTestSchema.count(HELD, tenant, role, action) == 1;
    }

    @Test
    @DisplayName("V148 backfills core.overtime.read to hr and payroll-officer of an existing tenant only")
    void backfillGrantsReadToHrAndPayrollOfficer() throws Exception {
        UUID tenant = OvertimeTestSchema.insertTenant("Seed " + UUID.randomUUID());
        // Make the tenant look as it did before V148: no role holds the read code.
        try (var conn = OvertimeTestSchema.migrationConnection();
                var ps = conn.prepareStatement("DELETE FROM core.role_action ra USING core.role r"
                        + " WHERE r.id = ra.role_id AND ra.tenant_id = ? AND ra.action_code = 'core.overtime.read'"
                        + " AND r.code IN ('hr', 'payroll-officer')")) {
            ps.setObject(1, tenant);
            ps.executeUpdate();
        }
        assertThat(holds(tenant, "hr", "core.overtime.read")).isFalse();

        OvertimeTestSchema.runMigration("core/V148__overtime_read_seed_roles.sql");

        assertThat(holds(tenant, "hr", "core.overtime.read")).isTrue();
        assertThat(holds(tenant, "payroll-officer", "core.overtime.read")).isTrue();
        assertThat(holds(tenant, "manager", "core.overtime.read")).isFalse();
        assertThat(holds(tenant, "employee", "core.overtime.read")).isFalse();
        // Guards against a drifted copy of V139's body.
        assertThat(holds(tenant, "hr", "core.attendance.read")).isTrue();
        assertThat(holds(tenant, "hr", "core.approval.decide")).isTrue();
        assertThat(holds(tenant, "payroll-officer", "core.attendance.read")).isTrue();
        assertThat(holds(tenant, "employee", "hrms.overtime.request")).isTrue();
    }

    @Test
    @DisplayName("hr may list overtime; list carries employee_name and get does not")
    void listCarriesNameGetDoesNot() throws Exception {
        UUID tenant = OvertimeTestSchema.insertTenant("Name " + UUID.randomUUID());
        UUID employee = OvertimeTestSchema.insertEmployee(tenant, "OT-NAME-" + UUID.randomUUID());
        UUID admin = UUID.randomUUID();
        UUID hr = UUID.randomUUID();
        OvertimeTestSchema.insertMember(tenant, admin, "tenant-admin");
        OvertimeTestSchema.insertMember(tenant, hr, "hr");

        String created = mvc.perform(authed(tenant, admin, post("/api/v1/overtime"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employee_id\": \"%s\", \"overtime_date\": \"2026-04-10\", \"hours\": 2}"
                                .formatted(employee)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee_name").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(json.readTree(created).get("id").asText());

        mvc.perform(authed(
                        tenant,
                        hr,
                        get("/api/v1/overtime").param("from", "2026-04-01").param("to", "2026-04-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee_id").value(employee.toString()))
                .andExpect(jsonPath("$[0].employee_name").value("Test"));

        TenantContext.set(tenant);
        assertThat(overtimeService.get(id).employeeName()).isNull();
    }

    private static MockHttpServletRequestBuilder authed(UUID tenant, UUID sub, MockHttpServletRequestBuilder builder) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@overtime.test")));
    }
}
