package com.infinevo.core.subscription;

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
 * Schema management and seed data for subscription integration tests (W-12.1).
 */
public final class SubscriptionTestSchema {

    public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String TENANT_SCOPE = " WHERE tenant_id IN"
            + " ('11111111-1111-1111-1111-111111111111',"
            + " '22222222-2222-2222-2222-222222222222')";

    private SubscriptionTestSchema() {}

    public static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    public static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    public static void apply() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!columnExists(conn, "tenant", "country_code")) {
                executeResource(conn, "db/migration/core/V033__tenant_locale_columns.sql");
            }
            if (!tableExists(conn, "subscription")) {
                executeResource(conn, "db/migration/core/V034__subscription.sql");
            }
            applySubscriptionPlan(conn);
        }
    }

    /**
     * W-84.1: brings {@code core.subscription} up to what the entity maps ({@code V188}). The whole script is run
     * when the tables its {@code core.list_tenants()} reads exist; otherwise only its column and setter sections,
     * cut out of the real resource, since a SQL function naming a missing table cannot be created.
     */
    public static void applySubscriptionPlan(Connection conn) throws Exception {
        if (columnExistsIn(conn, "subscription", "plan")) {
            return;
        }
        String sql = readV188();
        boolean listTenantsDependenciesExist = true;
        for (String table : new String[] {
            "employee", "user_account", "user_invitation", "user_invitation_role", "role", "subscription_module"
        }) {
            listTenantsDependenciesExist &= tableExistsIn(conn, table);
        }
        if (!listTenantsDependenciesExist) {
            int end = sql.indexOf("-- 3.");
            if (end < 0) {
                throw new IllegalStateException("V188__subscription_plan.sql no longer has its section 3");
            }
            sql = sql.substring(0, end);
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static String readV188() throws Exception {
        String resource = "db/migration/core/V188__subscription_plan.sql";
        try (InputStream in = SubscriptionTestSchema.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean tableExistsIn(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'core' AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExistsIn(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = 'core' AND table_name = ?"
                        + " AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT (tenant_id) DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Acme Manufacturing");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Globex Corporation");
            ps.executeUpdate();
        }
    }

    public static void clearAll() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            // Scope every delete to our own two test tenants so we never disturb rows
            // owned by other ITs running in the same shared Testcontainers database.
            stmt.execute("DELETE FROM core.subscription_module" + TENANT_SCOPE);
            stmt.execute("DELETE FROM core.subscription" + TENANT_SCOPE);
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, "core", table, new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, "core", table, column)) {
            return rs.next();
        }
    }

    private static void executeResource(Connection conn, String resource) throws Exception {
        try (InputStream in = SubscriptionTestSchema.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + resource);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
