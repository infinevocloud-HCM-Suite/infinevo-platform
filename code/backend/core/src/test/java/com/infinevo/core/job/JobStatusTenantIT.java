package com.infinevo.core.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = JobStatusTenantIT.TestApp.class)
class JobStatusTenantIT extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {}

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.POSTGRES_USER,
                PostgresTestContainerInitializer.POSTGRES_PASSWORD);
    }

    private static Connection migrationUserConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static Connection appUserConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    @BeforeAll
    static void applyMigrations() throws Exception {
        try (Connection conn = migrationUserConnection()) {
            executeSqlResource(conn, "db/migration/core/V001__tenant.sql");
            executeSqlResource(conn, "db/migration/core/V003__job_status_and_shedlock.sql");
        }
    }

    private static void executeSqlResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = JobStatusTenantIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }
        }
    }

    @BeforeEach
    void seedData() throws SQLException {
        TenantContext.clear();
        try (Connection conn = migrationUserConnection()) {
            conn.createStatement().execute("DELETE FROM core.job_status");
            conn.createStatement().execute("DELETE FROM core.tenant");

            try (PreparedStatement ps =
                    conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
                ps.setObject(1, TENANT_A);
                ps.setString(2, "Tenant A");
                ps.executeUpdate();

                ps.setObject(1, TENANT_B);
                ps.setString(2, "Tenant B");
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot view Tenant B's job record under RLS")
    void tenantIsolationOnJobStatus() throws SQLException {
        // Seed job for Tenant B using migration user
        try (Connection conn = migrationUserConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.job_status (job_id, tenant_id, queue_name, status) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, "job-tenant-b");
            ps.setObject(2, TENANT_B);
            ps.setString(3, "payrun");
            ps.setString(4, "RUNNING");
            ps.executeUpdate();
        }

        // Connect as app_user and bind to Tenant A
        try (Connection conn = appUserConnection()) {
            conn.setAutoCommit(false);
            TenantContext.set(TENANT_A);
            try {
                TenantContext.setForConnection(conn);

                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM core.job_status WHERE job_id = ?")) {
                    ps.setString(1, "job-tenant-b");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("Tenant A must not see Tenant B's job")
                                .isFalse();
                    }
                }
            } finally {
                TenantContext.clear();
            }
            conn.rollback();
        }
    }
}
