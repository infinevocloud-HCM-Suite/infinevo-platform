package com.infinevo.core.employee;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * W-84.2 — the employee limit over HTTP, against real Postgres: the subscription row is read through row-level
 * security as the bound tenant, and usage is the live count of employees not soft-deleted and not
 * {@code TERMINATED}.
 *
 * <p>Runs in {@link PermissionGuardTestApp}, which holds the subscription package; {@link AuthzTestSchema} brings
 * {@code core.subscription} up to {@code V188}, so {@code employee_limit} exists.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class EmployeeLimitIT extends AbstractIntegrationTest {

    private static final String JOINED = "2026-01-01";

    @Autowired
    private MockMvc mvc;

    private UUID tenantId;
    private UUID hrSub;

    @BeforeEach
    void seed() throws Exception {
        tenantId = AuthzTestSchema.insertTenant("EmployeeLimit " + UUID.randomUUID());
        TenantContext.set(tenantId);
        hrSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(tenantId, hrSub, "hr@limit.test");
        AuthzTestSchema.grant(tenantId, hrAccount, AuthzTestSchema.roleId(tenantId, "hr"));
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription (tenant_id, status) VALUES (?, 'ACTIVE')")) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("limit 2, two in use: create 409; terminate one, create 201; reactivating the leaver 409")
    void theGateOnEveryPath() throws Exception {
        setLimit(2);
        AuthzTestSchema.insertEmployee(tenantId, "A-1", "Asha");
        UUID leaver = AuthzTestSchema.insertEmployee(tenantId, "A-2", "Bina");
        // Neither of these is in use: one soft-deleted, one already terminated.
        sql(
                "UPDATE core.employee SET is_deleted = true WHERE id = ?",
                AuthzTestSchema.insertEmployee(tenantId, "D-1", "Dev"));
        sql(
                "UPDATE core.employee SET status = 'TERMINATED', termination_date = '2026-02-01' WHERE id = ?",
                AuthzTestSchema.insertEmployee(tenantId, "T-1", "Tara"));

        mvc.perform(as(get("/api/v1/employees/limit")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"limit\":2,\"used\":2}", true));

        mvc.perform(as(post("/api/v1/employees")).content(newEmployee("N-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message")
                        .value("Your plan allows 2 employees and 2 are in use. Contact Infinevo to raise the limit."));

        mvc.perform(as(put("/api/v1/employees/" + leaver))
                        .content(existingEmployee("\"status\":\"TERMINATED\",\"terminationDate\":\"2026-06-30\"")))
                .andExpect(status().isOk());

        mvc.perform(as(post("/api/v1/employees")).content(newEmployee("N-1"))).andExpect(status().isCreated());

        mvc.perform(as(put("/api/v1/employees/" + leaver)).content(existingEmployee("\"status\":\"ACTIVE\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LIMIT_REACHED"));

        mvc.perform(as(get("/api/v1/employees/limit")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"limit\":2,\"used\":2}", true));
    }

    @Test
    @DisplayName("a SUSPENDED employee counts")
    void suspendedCounts() throws Exception {
        setLimit(1);
        sql(
                "UPDATE core.employee SET status = 'SUSPENDED' WHERE id = ?",
                AuthzTestSchema.insertEmployee(tenantId, "S-1", "Sam"));

        mvc.perform(as(post("/api/v1/employees")).content(newEmployee("N-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Your plan allows 1 employees and 1 are in use. Contact Infinevo to raise the limit."));
    }

    @Test
    @DisplayName("limit NULL: no gate, ten creates go through")
    void nullLimitLetsTenThrough() throws Exception {
        setLimit(2);
        AuthzTestSchema.insertEmployee(tenantId, "A-1", "Asha");
        AuthzTestSchema.insertEmployee(tenantId, "A-2", "Bina");
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.subscription SET employee_limit = NULL WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }

        for (int n = 1; n <= 10; n++) {
            mvc.perform(as(post("/api/v1/employees")).content(newEmployee("N-" + n)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(as(get("/api/v1/employees/limit")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"limit\":null,\"used\":12}", true));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(b -> b.subject(hrSub.toString()).claim("tenant_id", tenantId.toString())));
    }

    private static String newEmployee(String number) {
        return "{\"employeeNumber\":\"" + number + "\",\"firstName\":\"New\",\"dateOfJoining\":\"" + JOINED + "\"}";
    }

    /** The seeded A-2, as a PUT body, with these extra fields. */
    private static String existingEmployee(String extra) {
        return "{\"employeeNumber\":\"A-2\",\"firstName\":\"Bina\",\"dateOfJoining\":\"" + JOINED + "\"," + extra + "}";
    }

    private void setLimit(int limit) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE core.subscription SET employee_limit = ? WHERE tenant_id = ?")) {
            ps.setInt(1, limit);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private static void sql(String statement, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(statement)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }
}
