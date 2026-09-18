package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.IOException;
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
import org.junit.jupiter.api.Test;

/**
 * W-07 — Integration test proving the tenant model and row-level security isolation.
 *
 * <p>Provisions the shared PostgreSQL Testcontainer, runs {@code V001__tenant.sql} as
 * {@code migration_user}, seeds two tenants (Tenant A and Tenant B), and asserts RLS
 * isolation probes (spec §4 breaks 3, 4, 5, 8, 9).
 */
@EnabledIfDockerAvailable
class TenantIsolationIT {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static String jdbcUrl;

    @BeforeAll
    static void setup() throws SQLException, IOException {
        jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();

        // Apply V001__tenant.sql as migration_user if table does not yet exist
        try (Connection conn = migrationUserConnection()) {
            boolean tableExists = false;
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = 'tenant'")) {
                tableExists = rs.next();
            }

            if (!tableExists) {
                try (InputStream is = TenantIsolationIT.class
                        .getClassLoader()
                        .getResourceAsStream("db/migration/core/V001__tenant.sql")) {
                    if (is == null) {
                        throw new IllegalStateException("V001__tenant.sql not found on classpath");
                    }
                    String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(sql);
                    }
                }
            }
        }
    }

    @BeforeEach
    void seedTenants() throws SQLException {
        TenantContext.clear();
        try (Connection conn = migrationUserConnection()) {
            conn.createStatement().execute("DELETE FROM core.tenant");
            try (PreparedStatement ps =
                    conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
                ps.setObject(1, TENANT_A);
                ps.setString(2, "Tenant Alpha");
                ps.executeUpdate();

                ps.setObject(1, TENANT_B);
                ps.setString(2, "Tenant Beta");
                ps.executeUpdate();
            }
        }
    }

    // ── Check: core.tenant exists, owned by migration_user, RLS enabled (relrowsecurity = true)

    @Test
    void tenantTableExistsAndRlsIsEnabled() throws SQLException {
        try (Connection conn = adminConnection();
                ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT c.relrowsecurity, t.tableowner "
                                + "FROM pg_class c "
                                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                                + "JOIN pg_tables t ON t.schemaname = n.nspname AND t.tablename = c.relname "
                                + "WHERE n.nspname = 'core' AND c.relname = 'tenant'")) {
            assertThat(rs.next()).as("core.tenant table exists").isTrue();
            assertThat(rs.getBoolean(1)).as("relrowsecurity on core.tenant").isTrue();
            assertThat(rs.getString(2)).as("table owner").isEqualTo(PostgresTestContainerInitializer.MIGRATION_USER);
        }
    }

    // ── §4 Break 3: app_user without session context sees 0 rows

    @Test
    void appUserWithoutContextSeesZeroRows() throws SQLException {
        try (Connection conn = appUserConnection();
                ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM core.tenant")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("count of rows visible without tenant context")
                    .isZero();
        }
    }

    // ── §4 Break 4 / Done-when 5: app_user with Tenant A context sees only Tenant A's row

    @Test
    void appUserWithTenantAContextSeesOnlyTenantARow() throws SQLException {
        try (Connection conn = appUserConnection()) {
            conn.setAutoCommit(false);
            TenantContext.set(TENANT_A);
            try {
                TenantContext.setForConnection(conn);

                try (ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT count(*), min(tenant_id::text), min(name) FROM core.tenant")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("visible row count for Tenant A")
                            .isEqualTo(1);
                    assertThat(rs.getString(2)).as("visible tenant_id").isEqualTo(TENANT_A.toString());
                    assertThat(rs.getString(3)).as("visible tenant name").isEqualTo("Tenant Alpha");
                }
            } finally {
                TenantContext.clear();
            }
            conn.rollback();
        }
    }

    // ── §4 Break 5: migration_user (table owner) bypasses RLS and sees all rows

    @Test
    void migrationUserBypassesRlsAndSeesAllRows() throws SQLException {
        try (Connection conn = migrationUserConnection();
                ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM core.tenant")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("all rows visible to migration_user").isEqualTo(2);
        }
    }

    // ── §4 Break 8: Cross-tenant INSERT rejected for app_user

    @Test
    void crossTenantInsertRejectedForAppUser() throws SQLException {
        try (Connection conn = appUserConnection()) {
            conn.setAutoCommit(false);
            TenantContext.set(TENANT_A);
            try {
                TenantContext.setForConnection(conn);

                try (PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
                    ps.setObject(1, TENANT_B);
                    ps.setString(2, "Stolen Tenant");

                    assertThatThrownBy(ps::executeUpdate)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("row-level security policy");
                }
            } finally {
                TenantContext.clear();
            }
            conn.rollback();
        }
    }

    // ── §4 Break 9: Cross-tenant UPDATE rejected for app_user

    @Test
    void crossTenantUpdateRejectedForAppUser() throws SQLException {
        try (Connection conn = appUserConnection()) {
            conn.setAutoCommit(false);
            TenantContext.set(TENANT_A);
            try {
                TenantContext.setForConnection(conn);

                try (PreparedStatement ps =
                        conn.prepareStatement("UPDATE core.tenant SET tenant_id = ? WHERE tenant_id = ?")) {
                    ps.setObject(1, TENANT_B);
                    ps.setObject(2, TENANT_A);

                    assertThatThrownBy(ps::executeUpdate)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("row-level security policy");
                }
            } finally {
                TenantContext.clear();
            }
            conn.rollback();
        }
    }

    // ── Positive DML: app_user can update its own row within its tenant context

    @Test
    void appUserCanUpdateOwnTenantRecord() throws SQLException {
        try (Connection conn = appUserConnection()) {
            conn.setAutoCommit(false);
            TenantContext.set(TENANT_A);
            try {
                TenantContext.setForConnection(conn);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.tenant SET name = 'Tenant Alpha Updated' WHERE tenant_id = ?")) {
                    ps.setObject(1, TENANT_A);
                    int updated = ps.executeUpdate();
                    assertThat(updated).isEqualTo(1);
                }

                try (ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT name FROM core.tenant WHERE tenant_id = '" + TENANT_A + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("Tenant Alpha Updated");
                }
            } finally {
                TenantContext.clear();
            }
            conn.rollback();
        }
    }

    // ── helpers

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static Connection migrationUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static Connection appUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl, PostgresTestContainerInitializer.APP_USER, PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }
}
