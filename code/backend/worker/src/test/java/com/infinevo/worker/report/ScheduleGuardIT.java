package com.infinevo.worker.report;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * Integration test verifying @RequiresAction guards on report-schedule and job-status endpoints (W-23.2 §7).
 *
 * <p>Proves:
 * <ul>
 *   <li>{@code GET /api/v1/report-schedules} returns {@code 403} without {@code core.report_schedule.manage}.</li>
 *   <li>{@code PUT /api/v1/report-schedules/{id}} returns {@code 403} without {@code core.report_schedule.manage}.</li>
 *   <li>{@code GET /api/v1/jobs/{id}} returns {@code 403} without {@code core.job.read}.</li>
 *   <li>{@code GET /api/v1/jobs/{id}} returns {@code 404} for a job belonging to another tenant.</li>
 * </ul>
 */
@SpringBootTest(
        classes = com.infinevo.worker.InfinevoWorkerApplication.class,
        properties = {"infinevo.cache.enabled=false"})
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            ReportWorkerTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ScheduleGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    /* two tenants and users seeded once per class */
    private static UUID tenantA;
    private static UUID tenantB;

    private UUID adminSub; // holds core.report_schedule.manage + core.job.read
    private UUID employeeSub; // holds nothing

    private static UUID defId;
    private static final String JOB_OF_A = "guard-job-a-" + UUID.randomUUID();
    private static final String JOB_OF_B = "guard-job-b-" + UUID.randomUUID();

    @BeforeAll
    static void initSchema() throws Exception {
        ReportWorkerTestSchema.jdbcUrl();
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        defId = UUID.randomUUID();

        try (Connection conn = ReportWorkerTestSchema.migrationConnection()) {
            ReportWorkerTestSchema.insertTenant(conn, tenantA, "Guard Tenant A", "UTC");
            ReportWorkerTestSchema.insertTenant(conn, tenantB, "Guard Tenant B", "UTC");
            ReportWorkerTestSchema.insertReportDefinition(
                    conn,
                    defId,
                    tenantA,
                    "GUARD_DEF",
                    "Guard Export",
                    "audit_log",
                    "[\"occurred_at\"]",
                    "core.audit.read",
                    "CSV");

            // Seed jobs for cross-tenant 404 test
            seedJobs(conn);
        }
    }

    private static void seedJobs(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO core.job_status (job_id, tenant_id, queue_name, status) VALUES ('" + JOB_OF_A
                    + "', '" + tenantA + "', 'report', 'RUNNING') ON CONFLICT DO NOTHING");
            stmt.execute("INSERT INTO core.job_status (job_id, tenant_id, queue_name, status) VALUES ('" + JOB_OF_B
                    + "', '" + tenantB + "', 'report', 'COMPLETED') ON CONFLICT DO NOTHING");
        }
    }

    @BeforeEach
    void seedMembers() throws Exception {
        adminSub = UUID.randomUUID();
        employeeSub = UUID.randomUUID();

        try (Connection conn = ReportWorkerTestSchema.migrationConnection()) {
            // Tenant A — admin holds the two actions needed for schedule + job endpoints
            insertMember(conn, tenantA, adminSub, "core.report_schedule.manage", "core.job.read", "core.audit.read");
            // Tenant A — employee holds no actions
            insertMember(conn, tenantA, employeeSub);
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Report schedule guard ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /report-schedules: 403 without core.report_schedule.manage")
    void getSchedulesRefusedWithoutManage() throws Exception {
        mvc.perform(as(employeeSub, get("/api/v1/report-schedules")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.report_schedule.manage")));
    }

    @Test
    @DisplayName("GET /report-schedules: 200 with core.report_schedule.manage")
    void getSchedulesAllowedWithManage() throws Exception {
        mvc.perform(as(adminSub, get("/api/v1/report-schedules"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /report-schedules/{id}: 403 without core.report_schedule.manage")
    void putScheduleRefusedWithoutManage() throws Exception {
        String body =
                """
            {"definitionId": "%s", "cadence": "DAILY", "sendAtLocalTime": "09:00:00",
             "recipientEmails": "t@t.com", "isActive": true}
            """
                        .formatted(defId);

        mvc.perform(as(employeeSub, put("/api/v1/report-schedules/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.report_schedule.manage")));
    }

    // ── Job status guard ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /jobs/{id}: 403 without core.job.read")
    void getJobRefusedWithoutJobRead() throws Exception {
        mvc.perform(as(employeeSub, get("/api/v1/jobs/" + JOB_OF_A)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.job.read")));
    }

    @Test
    @DisplayName("GET /jobs/{id}: 200 for own tenant's job with core.job.read")
    void getOwnJobAllowedWithJobRead() throws Exception {
        mvc.perform(as(adminSub, get("/api/v1/jobs/" + JOB_OF_A))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /jobs/{id}: 404 for another tenant's job — RLS hides it")
    void getOtherTenantJobReturns404() throws Exception {
        // adminSub belongs to Tenant A; JOB_OF_B belongs to Tenant B
        mvc.perform(as(adminSub, get("/api/v1/jobs/" + JOB_OF_B))).andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder builder) {
        return builder.header("X-Tenant-ID", tenantA.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@guard.test")));
    }

    private static void insertMember(Connection conn, UUID tenantId, UUID sub, String... actions) throws Exception {
        UUID accountId = UUID.randomUUID();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.user_tenant (tenant_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, sub);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.user_account (id, tenant_id, keycloak_user_id, email, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, 'test', 'test') ON CONFLICT DO NOTHING")) {
            ps.setObject(1, accountId);
            ps.setObject(2, tenantId);
            ps.setObject(3, sub);
            ps.setString(4, sub + "@guard.test");
            ps.executeUpdate();
        }

        if (actions.length > 0) {
            UUID roleId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.role (id, tenant_id, code, name) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
                String code = "guard-role-" + sub;
                ps.setObject(1, roleId);
                ps.setObject(2, tenantId);
                ps.setString(3, code);
                ps.setString(4, code);
                ps.executeUpdate();
            }
            for (String action : actions) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES (?, ?, ?)"
                                + " ON CONFLICT DO NOTHING")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, roleId);
                    ps.setString(3, action);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES (?, ?, ?)"
                            + " ON CONFLICT DO NOTHING")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, accountId);
                ps.setObject(3, roleId);
                ps.executeUpdate();
            }
        }
    }
}
