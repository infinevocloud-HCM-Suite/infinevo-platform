package com.infinevo.core.overtime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * W-39.2 §7 — {@code manage} and {@code read} are two codes, not one: a caller holding only
 * {@code core.overtime.read} must be refused on the write and cancel endpoints, the same rule
 * {@code PayInputGuardIT} proves for the ledger's own endpoints.
 */
@SpringBootTest(classes = OvertimeTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            OvertimeTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class OvertimePermissionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private UUID tenant;
    private UUID admin;
    private UUID readOnly;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = OvertimeTestSchema.insertTenant("Guard " + UUID.randomUUID());
        employee = OvertimeTestSchema.insertEmployee(tenant, "OT-GUARD-" + UUID.randomUUID());
        admin = UUID.randomUUID();
        readOnly = UUID.randomUUID();
        OvertimeTestSchema.insertMember(tenant, admin, "tenant-admin");
        OvertimeTestSchema.insertMemberWithActions(tenant, readOnly, "core.overtime.read");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@overtime.test")));
    }

    @Test
    @DisplayName("GET /api/v1/overtime: 200 for read-only and for tenant-admin")
    void getIsAllowedForReadAndForAdmin() throws Exception {
        mvc.perform(authed(get("/api/v1/overtime").param("from", "2026-04-01").param("to", "2026-04-30"), readOnly))
                .andExpect(status().isOk());
        mvc.perform(authed(get("/api/v1/overtime").param("from", "2026-04-01").param("to", "2026-04-30"), admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/overtime: 403 for read-only, 201 for tenant-admin")
    void postIsRefusedForReadOnly() throws Exception {
        String body =
                """
            {
              "employee_id": "%s",
              "overtime_date": "2026-04-10",
              "hours": 2
            }
            """
                        .formatted(employee);

        mvc.perform(authed(
                        post("/api/v1/overtime")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        readOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.overtime.manage")));

        mvc.perform(authed(
                        post("/api/v1/overtime")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        admin))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DELETE /api/v1/overtime/{id}: 403 for read-only")
    void cancelIsRefusedForReadOnly() throws Exception {
        mvc.perform(authed(delete("/api/v1/overtime/" + UUID.randomUUID()), readOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.overtime.manage")));
    }
}
