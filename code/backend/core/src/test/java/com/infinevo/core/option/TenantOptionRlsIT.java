package com.infinevo.core.option;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
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

/**
 * Integration test for tenant isolation on {@code core.tenant_option} (W-104 §7).
 *
 * <p>Validates that:
 * <ul>
 *   <li>Tenant A cannot read, update, or delete Tenant B's option rows as {@code app_user};
 *   <li>An unbound connection sees zero rows under fail-safe RLS.
 * </ul>
 */
@EnabledIfDockerAvailable
class TenantOptionRlsIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("a1000000-0000-4000-8000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("b1000000-0000-4000-8000-00000000000b");

    @BeforeAll
    static void applySchema() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "core", "tenant")) {
                executeSqlResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "core", "tenant_option")) {
                executeSqlResource(conn, "db/migration/core/V200__tenant_option.sql");
            }
        }
    }

    @BeforeEach
    void seed() throws Exception {
        try (Connection conn = migrationConnection()) {
            // Ensure tenants exist
            seedTenant(conn, TENANT_A, "Tenant A");
            seedTenant(conn, TENANT_B, "Tenant B");

            // Clear existing option rows
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                        "DELETE FROM core.tenant_option WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
            }

            // Seed one option for A and one for B
            seedOption(conn, TENANT_A, "core.test.key", "value_a");
            seedOption(conn, TENANT_B, "core.test.key", "value_b");
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant A sees only Tenant A's row")
    void appUserBoundToTenantASeesOnlyOwnRow() throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            bind(conn, TENANT_A);

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.tenant_option")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT count(*) FROM core.tenant_option WHERE tenant_id = '" + TENANT_B + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }

            conn.rollback();
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant A cannot update Tenant B's rows")
    void appUserCannotUpdateOtherTenantRows() throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            bind(conn, TENANT_A);

            try (Statement stmt = conn.createStatement()) {
                int updated = stmt.executeUpdate(
                        "UPDATE core.tenant_option SET option_value = 'hacked' WHERE tenant_id = '" + TENANT_B + "'");
                assertThat(updated).isZero();
            }

            conn.rollback();
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant A cannot delete Tenant B's rows")
    void appUserCannotDeleteOtherTenantRows() throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            bind(conn, TENANT_A);

            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate("DELETE FROM core.tenant_option WHERE tenant_id = '" + TENANT_B + "'");
                assertThat(deleted).isZero();
            }

            conn.rollback();
        }
    }

    @Test
    @DisplayName("app_user with no tenant bound sees no option rows")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.tenant_option")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    // ── Helper methods ──

    private static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    private static void bind(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private static void seedTenant(Connection conn, UUID tenantId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT (tenant_id) DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private static void seedOption(Connection conn, UUID tenantId, String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.tenant_option (tenant_id, option_key, option_value) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void executeSqlResource(Connection conn, String resourcePath) throws Exception {
        ClassLoader cl = TenantOptionRlsIT.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
