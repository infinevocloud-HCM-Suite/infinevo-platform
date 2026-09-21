package com.infinevo.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = TenantBindingIT.TestApp.class)
@AutoConfigureMockMvc
class TenantBindingIT extends AbstractIntegrationTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            boolean tenantExists = false;
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = 'tenant'")) {
                tenantExists = rs.next();
            }

            if (!tenantExists) {
                executeSqlScript(conn, "db/migration/core/V001__tenant.sql");
            }

            boolean userTenantExists = false;
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
        try (var is = TenantBindingIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                String sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                conn.createStatement().execute(sql);
            }
        }
    }

    @SpringBootApplication(scanBasePackages = "com.infinevo.shared")
    static class TestApp {

        /**
         * A plain JDBC transaction manager, so {@code @Transactional} below binds the very
         * connection {@link JdbcTemplate} then uses. The tenant binding is transaction-local
         * (D-57), so the endpoint must genuinely run inside a transaction.
         */
        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @RestController
        static class TestController {

            @Autowired
            private JdbcTemplate jdbcTemplate;

            @GetMapping("/actuator/health")
            public String health() {
                return "{\"status\":\"UP\"}";
            }

            @GetMapping("/v1/test-protected")
            @Transactional
            public Map<String, Object> testProtected() {
                UUID boundTenant = TenantContext.current().orElse(null);
                Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM core.tenant", Integer.class);
                return Map.of(
                        "boundTenant",
                        boundTenant != null ? boundTenant.toString() : "none",
                        "rowCount",
                        count == null ? 0 : count);
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantA = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("b2222222-2222-2222-2222-222222222222");

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
                    stmt.execute("INSERT INTO core.tenant (tenant_id, name) VALUES ('" + tenantA
                            + "', 'Tenant A') ON CONFLICT DO NOTHING");
                    stmt.execute("INSERT INTO core.tenant (tenant_id, name) VALUES ('" + tenantB
                            + "', 'Tenant B') ON CONFLICT DO NOTHING");
                    stmt.execute("INSERT INTO core.user_tenant (user_id, tenant_id) VALUES ('" + userId + "', '"
                            + tenantA + "') ON CONFLICT DO NOTHING");
                }
                conn.commit();
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    @Test
    @DisplayName("Exempt public endpoint passes through without requiring authentication or tenant binding")
    void exemptEndpoint_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName(
            "Real HTTP request with valid tenant header returns 200 OK, binds tenant context, and session binder executes DB query under RLS")
    void validTenantRequest_returns200() throws Exception {
        mockMvc.perform(get("/v1/test-protected")
                        .header("X-Tenant-Id", tenantA.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boundTenant").value(tenantA.toString()))
                .andExpect(jsonPath("$.rowCount").value(1));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("Real HTTP request with unpermitted tenant header returns 403 FORBIDDEN")
    void unpermittedTenantRequest_returns403() throws Exception {
        mockMvc.perform(get("/v1/test-protected")
                        .header("X-Tenant-Id", tenantB.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "99999999-9999-9999-9999-999999999999")
    @DisplayName("Real HTTP request with missing tenant for unmapped user returns 401 TENANT_NOT_BOUND")
    void missingTenantRequest_unmappedUser_returns401() throws Exception {
        mockMvc.perform(get("/v1/test-protected").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_BOUND"));
    }

    @Test
    @DisplayName(
            "unboundQuery_returnsZeroRows_underRLS: Direct DB query executed without bound tenant returns 0 rows under RLS (D-56)")
    void unboundQuery_returnsZeroRows_underRLS() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                // Ensure session variable app.current_tenant_id is NOT bound / empty
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SELECT set_config('app.current_tenant_id', '', true)");
                    try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.tenant")) {
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            assertThat(count).isEqualTo(0);
                        }
                    }
                }
                conn.commit();
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }
}
