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

    /**
     * Applies the two migrations this test needs, each only if its table is absent.
     *
     * <p>The guard is not decoration. The Postgres container is shared by every integration test in
     * the module, so whichever class runs first creates {@code core.tenant} and the next one to run
     * {@code V001__tenant.sql} unguarded dies on {@code relation "tenant" already exists} — which is
     * what happened the moment W-13.1 added {@code EmployeeRlsIT}, a class that sorts before this one
     * and needs the same table.
     */
    @BeforeAll
    static void applyMigrations() throws Exception {
        try (Connection conn = migrationUserConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeSqlResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "job_status")) {
                executeSqlResource(conn, "db/migration/core/V006__job_status_and_shedlock.sql");
            }
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
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
            if (tableExists(conn, "user_tenant")) {
                conn.createStatement().execute("DELETE FROM core.user_tenant");
            }
            if (tableExists(conn, "user_role")) {
                conn.createStatement().execute("DELETE FROM core.user_role");
            }
            if (tableExists(conn, "user_role_assignment")) {
                conn.createStatement().execute("DELETE FROM core.user_role_assignment");
            }
            if (tableExists(conn, "role_action")) {
                conn.createStatement().execute("DELETE FROM core.role_action");
            }
            if (tableExists(conn, "role")) {
                conn.createStatement().execute("DELETE FROM core.role");
            }
            if (tableExists(conn, "employee_contact")) {
                conn.createStatement().execute("DELETE FROM core.employee_contact");
            }
            if (tableExists(conn, "employee_personal")) {
                conn.createStatement().execute("DELETE FROM core.employee_personal");
            }
            if (tableExists(conn, "employee")) {
                conn.createStatement().execute("DELETE FROM core.employee");
            }
            if (tableExists(conn, "user_account")) {
                conn.createStatement().execute("DELETE FROM core.user_account");
            }
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
