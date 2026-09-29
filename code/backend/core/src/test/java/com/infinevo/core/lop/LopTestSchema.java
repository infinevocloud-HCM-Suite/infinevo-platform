package com.infinevo.core.lop;

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
 * Schema management and seed data for loss-of-pay integration tests (W-18.1).
 */
public final class LopTestSchema {

    public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private LopTestSchema() {}

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
            if (!tableExists(conn, "work_location")) {
                executeResource(conn, "db/migration/core/V013__work_location.sql");
            }
            if (!tableExists(conn, "employee")) {
                executeResource(conn, "db/migration/core/V010__employee.sql");
            }
            if (!tableExists(conn, "holiday_calendar")) {
                executeResource(conn, "db/migration/core/V036__holiday_calendar.sql");
            }
            if (!tableExists(conn, "lop_policy")) {
                executeResource(conn, "db/migration/core/V116__lop_policy.sql");
            }
        }
    }

    public static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT (tenant_id) DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Tenant Alpha");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Tenant Beta");
            ps.executeUpdate();
        }
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                    INSERT INTO core.lop_policy (id, tenant_id, working_day_basis, weekends_payable,
                        holidays_payable, lop_rounding, effective_from)
                    SELECT gen_random_uuid(), t.tenant_id, 'ACTUAL_DAYS', true, true, 'HALF_UP_2', '1900-01-01'
                    FROM core.tenant t
                    WHERE NOT EXISTS (SELECT 1 FROM core.lop_policy p WHERE p.tenant_id = t.tenant_id)
                    """);
        }
    }

    public static void clearAll() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "lop_policy")) {
                stmt.execute("DELETE FROM core.lop_policy");
            }
            if (tableExists(conn, "holiday_calendar_location")) {
                stmt.execute("DELETE FROM core.holiday_calendar_location");
            }
            if (tableExists(conn, "holiday")) {
                stmt.execute("DELETE FROM core.holiday");
            }
            if (tableExists(conn, "holiday_calendar")) {
                stmt.execute("DELETE FROM core.holiday_calendar");
            }
            if (tableExists(conn, "employee")) {
                stmt.execute("DELETE FROM core.employee");
            }
            if (tableExists(conn, "work_location")) {
                stmt.execute("DELETE FROM core.work_location");
            }
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'core' AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, "core", table, column)) {
            return rs.next();
        }
    }

    private static void executeResource(Connection conn, String path) throws Exception {
        ClassLoader cl = LopTestSchema.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
