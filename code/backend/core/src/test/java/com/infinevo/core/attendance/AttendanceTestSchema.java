package com.infinevo.core.attendance;

import com.infinevo.core.employee.EmployeeTestSchema;
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
 * Test schema and seed helpers for attendance tests (W-39.1).
 */
public final class AttendanceTestSchema {

    public static final UUID TENANT_A = EmployeeTestSchema.TENANT_A;
    public static final UUID TENANT_B = EmployeeTestSchema.TENANT_B;

    private AttendanceTestSchema() {}

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
        EmployeeTestSchema.apply();
        try (Connection conn = migrationConnection()) {
            if (!columnExists(conn, "tenant", "timezone")) {
                executeResource(conn, "db/migration/core/V033__tenant_locale_columns.sql");
            }
            if (!tableExists(conn, "audit_log")) {
                executeResource(conn, "db/migration/core/V008__audit_log.sql");
            }
            if (!tableExists(conn, "attendance")) {
                executeResource(conn, "db/migration/core/V030__attendance.sql");
            }
        }
    }

    public static void clearAttendance() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "attendance")) {
                stmt.execute("DELETE FROM core.attendance");
            }
        }
    }

    public static void clearAudit() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "audit_log")) {
                stmt.execute("DELETE FROM core.audit_log");
            }
        }
    }

    public static int visibleRowCount(String table, UUID tenantId) throws SQLException {
        try (Connection conn = appConnection();
                Statement set = conn.createStatement();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core." + table)) {
            set.execute("SET app.current_tenant_id = '" + tenantId + "'");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static int rawRowCount(String table) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core." + table);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'core' AND table_name = ?
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'core' AND table_name = ? AND column_name = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void executeResource(Connection conn, String path) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement s = conn.createStatement()) {
                s.execute(sql);
            }
        }
    }
}
