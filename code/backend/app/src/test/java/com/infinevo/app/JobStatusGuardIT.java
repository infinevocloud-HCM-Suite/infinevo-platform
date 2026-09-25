package com.infinevo.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = JobStatusGuardIT.TestApp.class)
@AutoConfigureMockMvc
class JobStatusGuardIT extends AbstractIntegrationTest {

    @SpringBootApplication(
            scanBasePackages = {"com.infinevo.app", "com.infinevo.shared"},
            exclude = {FlywayAutoConfiguration.class})
    static class TestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeAll
    static void applyMigrations() throws Exception {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {

            boolean tenantExists;
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = 'tenant'")) {
                tenantExists = rs.next();
            }

            if (!tenantExists) {
                executeSqlScript(conn, "db/migration/core/V001__tenant.sql");
            }

            boolean userTenantExists;
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = 'user_tenant'")) {
                userTenantExists = rs.next();
            }

            if (!userTenantExists) {
                executeSqlScript(conn, "db/migration/core/V002__user_tenant.sql");
            }
        }
    }

    private static void executeSqlScript(Connection conn, String resourcePath) throws Exception {
        try (var is = JobStatusGuardIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                String sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                conn.createStatement().execute(sql);
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
                    stmt.execute("INSERT INTO core.user_tenant (user_id, tenant_id) VALUES ('" + USER_ID + "', '"
                            + TENANT_A + "') ON CONFLICT DO NOTHING");
                }
                conn.commit();
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    @Test
    @DisplayName("Returns 403 Forbidden when caller lacks core.job.read permission")
    @WithMockUser(username = USER_ID)
    void forbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/job-100").header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Returns 404 Not Found when requested job belongs to another tenant or not found")
    @WithMockUser(username = USER_ID, authorities = "core.job.read")
    void notFoundForOtherTenantJob() throws Exception {
        BDDMockito.given(jobService.getJobStatus("job-other", TENANT_A)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jobs/job-other").header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isNotFound());
    }
}
