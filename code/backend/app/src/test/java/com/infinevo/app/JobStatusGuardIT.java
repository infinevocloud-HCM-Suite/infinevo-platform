package com.infinevo.app;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-52.1 — {@code GET /api/v1/jobs/{jobId}} through the real guard and the real service.
 *
 * <p>{@code JobService} is not mocked: a mock returns empty for every call, so a 404 would
 * pass whatever the tenant was. Instead a job is seeded for Tenant B and Tenant A's caller must
 * get 404 for it and 200 for its own.
 */
@SpringBootTest(classes = JobStatusGuardIT.TestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, RedisTestContainerInitializer.class})
class JobStatusGuardIT extends AbstractIntegrationTest {

    @SpringBootApplication(
            scanBasePackages = {
                "com.infinevo.app",
                "com.infinevo.shared",
                "com.infinevo.core.authz",
                "com.infinevo.core.job"
            },
            exclude = {FlywayAutoConfiguration.class})
    // No @EnableJpaRepositories / @EntityScan here: scanning com.infinevo.app picks up
    // InfinevoApplication, which already declares both over com.infinevo.
    static class TestApp {}

    @Autowired
    private MockMvc mockMvc;

    private static final UUID UNAUTHORIZED_SUB = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AUTHORIZED_SUB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID UNAUTHORIZED_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-999999999999");
    private static final UUID AUTHORIZED_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-999999999999");
    private static final UUID JOB_READ_ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final String JOB_OF_A = "job-guard-a";
    private static final String JOB_OF_B = "job-guard-b";

    @BeforeAll
    static void applyMigrations() throws Exception {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {

            if (!tableExists(conn, "core", "user_role")) {
                executeSqlScript(conn, "db/migration/core/V001__tenant.sql");
                executeSqlScript(conn, "db/migration/core/V002__user_tenant.sql");
                executeSqlScript(conn, "db/migration/core/V009__user_account.sql");
                executeSqlScript(conn, "db/migration/reference/V020__action.sql");
                executeSqlScript(conn, "db/migration/core/V021__role.sql");
                executeSqlScript(conn, "db/migration/core/V022__role_action.sql");
                executeSqlScript(conn, "db/migration/core/V023__user_role.sql");
                executeSqlScript(conn, "db/migration/core/V025__catalogue_correction.sql");
            }
            if (!tableExists(conn, "core", "job_status")) {
                executeSqlScript(conn, "db/migration/core/V006__job_status_and_shedlock.sql");
            }
        }
    }

    private static boolean tableExists(Connection conn, String schema, String table) throws Exception {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = ? AND tablename = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Fails loudly on a missing script: a silently skipped migration leaves a fixture half built. */
    private static void executeSqlScript(Connection conn, String resourcePath) throws Exception {
        try (var is = JobStatusGuardIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Migration script not on the test classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    @BeforeEach
    void setUpDatabase() throws Exception {
        TenantContext.clear();
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("INSERT INTO core.tenant (tenant_id, name) VALUES ('" + TENANT_A
                            + "', 'Tenant A') ON CONFLICT DO NOTHING");
                    stmt.execute("INSERT INTO core.tenant (tenant_id, name) VALUES ('" + TENANT_B
                            + "', 'Tenant B') ON CONFLICT DO NOTHING");

                    stmt.execute("INSERT INTO core.user_tenant (tenant_id, user_id) VALUES ('" + TENANT_A + "', '"
                            + UNAUTHORIZED_SUB + "') ON CONFLICT DO NOTHING");
                    stmt.execute("INSERT INTO core.user_tenant (tenant_id, user_id) VALUES ('" + TENANT_A + "', '"
                            + AUTHORIZED_SUB + "') ON CONFLICT DO NOTHING");

                    stmt.execute(
                            "INSERT INTO core.user_account (id, tenant_id, keycloak_user_id, email, created_by, updated_by) VALUES ('"
                                    + UNAUTHORIZED_ACCOUNT_ID + "', '" + TENANT_A + "', '" + UNAUTHORIZED_SUB
                                    + "', 'unauth@test.com', 'test', 'test') ON CONFLICT DO NOTHING");
                    stmt.execute(
                            "INSERT INTO core.user_account (id, tenant_id, keycloak_user_id, email, created_by, updated_by) VALUES ('"
                                    + AUTHORIZED_ACCOUNT_ID + "', '" + TENANT_A + "', '" + AUTHORIZED_SUB
                                    + "', 'auth@test.com', 'test', 'test') ON CONFLICT DO NOTHING");

                    stmt.execute("INSERT INTO core.role (id, tenant_id, code, name) VALUES ('" + JOB_READ_ROLE_ID
                            + "', '" + TENANT_A + "', 'job-reader', 'Job Reader') ON CONFLICT DO NOTHING");

                    stmt.execute("INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES ('" + TENANT_A
                            + "', '" + JOB_READ_ROLE_ID + "', 'core.job.read') ON CONFLICT DO NOTHING");

                    stmt.execute("INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES ('" + TENANT_A
                            + "', '" + AUTHORIZED_ACCOUNT_ID + "', '" + JOB_READ_ROLE_ID + "') ON CONFLICT DO NOTHING");

                    // One job per tenant. The migration user bypasses RLS, so both rows land.
                    stmt.execute("INSERT INTO core.job_status (job_id, tenant_id, queue_name, status) VALUES ('"
                            + JOB_OF_A + "', '" + TENANT_A + "', 'payrun', 'RUNNING') ON CONFLICT DO NOTHING");
                    stmt.execute("INSERT INTO core.job_status (job_id, tenant_id, queue_name, status) VALUES ('"
                            + JOB_OF_B + "', '" + TENANT_B + "', 'payrun', 'COMPLETED') ON CONFLICT DO NOTHING");
                }
                conn.commit();
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", TENANT_A.toString())));
    }

    @Test
    @DisplayName("Returns 403 Forbidden when caller lacks core.job.read permission")
    void forbiddenWithoutPermission() throws Exception {
        mockMvc.perform(as(UNAUTHORIZED_SUB, get("/api/v1/jobs/" + JOB_OF_A))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Returns 200 with the job when it belongs to the caller's tenant")
    void okForOwnTenantJob() throws Exception {
        mockMvc.perform(as(AUTHORIZED_SUB, get("/api/v1/jobs/" + JOB_OF_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(JOB_OF_A))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("Returns 404 Not Found when the job belongs to another tenant")
    void notFoundForOtherTenantJob() throws Exception {
        mockMvc.perform(as(AUTHORIZED_SUB, get("/api/v1/jobs/" + JOB_OF_B))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Returns 404 Not Found when the job does not exist")
    void notFoundForUnknownJob() throws Exception {
        mockMvc.perform(as(AUTHORIZED_SUB, get("/api/v1/jobs/no-such-job"))).andExpect(status().isNotFound());
    }
}
