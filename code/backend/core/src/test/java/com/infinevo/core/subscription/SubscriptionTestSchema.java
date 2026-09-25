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
            stmt.execute("DELETE FROM core.subscription_module");
            stmt.execute("DELETE FROM core.subscription");
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
