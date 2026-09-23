package com.infinevo.shared.identity;

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

/**
 * Schema and seed data shared by the identity integration tests.
 *
 * <p>The shipped migrations are applied as written — {@code V001__tenant.sql},
 * {@code V002__user_tenant.sql} and {@code V009__user_account.sql} — so the table under test is the
 * migrated one and not a copy of it that has drifted.
 *
 * <p>The tenant and user UUIDs are the ones the local stack uses, from
 * {@code infra/docker/seed/01-tenants.sql} and {@code infra/docker/keycloak/dev-realm.json}. That
 * matters for {@code LoginFlowIT}: the token minted by the realm carries {@code sub} and
 * {@code tenant_id} claims, and they have to find a membership row here for the same reason they do
 * locally.
 */
final class IdentityTestSchema {

    /** Acme Manufacturing — {@code infra/docker/seed/01-tenants.sql}. */
    static final UUID TENANT_ACME = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Globex Corporation — {@code infra/docker/seed/01-tenants.sql}. */
    static final UUID TENANT_GLOBEX = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** {@code admin.acme} — {@code infra/docker/keycloak/dev-realm.json}. */
    static final UUID USER_ADMIN_ACME = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    /** {@code admin.globex}, Gita Globex — {@code infra/docker/keycloak/dev-realm.json}. */
    static final UUID USER_ADMIN_GLOBEX = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    private IdentityTestSchema() {}

    static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    /** Applies the shipped migrations this feature needs. Idempotent — other suites share the container. */
    static void apply() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "user_tenant")) {
                executeResource(conn, "db/migration/core/V002__user_tenant.sql");
            }
            if (!tableExists(conn, "user_account")) {
                executeResource(conn, "db/migration/core/V009__user_account.sql");
            }
        }
    }

    /** Seeds the two dev tenants. Idempotent. */
    static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT_ACME);
            ps.setString(2, "Acme Manufacturing");
            ps.executeUpdate();
            ps.setObject(1, TENANT_GLOBEX);
            ps.setString(2, "Globex Corporation");
            ps.executeUpdate();
        }
    }

    /**
     * Binds a user to a tenant, the way {@code infra/docker/seed/02-user-tenants.sql} does locally.
     * Without one of these rows {@code TenantContextFilter} answers {@code 401 TENANT_NOT_BOUND} —
     * spec section 13, decision 2.
     */
    static void seedMembership(UUID userId, UUID tenantId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.user_tenant (tenant_id, user_id, created_by, updated_by)
                        VALUES (?, ?, 'test', 'test')
                        ON CONFLICT (user_id, tenant_id) DO NOTHING
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
    }

    /** Inserts a profile row directly, as the schema owner, so row-level security can be tested against it. */
    static void seedUserAccount(UUID tenantId, UUID keycloakUserId, String email, String firstName, String lastName)
            throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.user_account
                            (tenant_id, keycloak_user_id, email, first_name, last_name, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, 'test', 'test')
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, keycloakUserId);
            ps.setString(3, email);
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            ps.executeUpdate();
        }
    }

    /** Removes every profile row, as the schema owner. For {@code @BeforeEach}. */
    static void clearUserAccounts() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM core.user_account");
        }
    }

    /** Counts profile rows for one tenant, bypassing row-level security — the control for the assertions. */
    static int countUserAccounts(UUID tenantId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.user_account WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** One column of the one profile row, read as the schema owner. */
    static Object readColumn(UUID tenantId, UUID keycloakUserId, String column) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT " + column + " FROM core.user_account WHERE tenant_id = ? AND keycloak_user_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, keycloakUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
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

    private static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = IdentityTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("migration not on the test classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
