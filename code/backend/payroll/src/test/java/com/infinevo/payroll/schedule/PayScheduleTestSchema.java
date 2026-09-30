package com.infinevo.payroll.schedule;

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
 * Schema management and seed data for pay schedule integration tests (W-28).
 *
 * <p>Applies all prerequisite migration scripts to the shared {@code infinevo} test database.
 * Uses {@code migration_user} for DDL and insert; individual tests query via {@code app_user}
 * to exercise row-level security.
 */
final class PayScheduleTestSchema {

    public static final UUID TENANT_A = UUID.fromString("aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa");
    public static final UUID TENANT_B = UUID.fromString("bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb");

    private PayScheduleTestSchema() {}

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
            if (!tableExists(conn, "core", "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            // Another test class in this JVM may already have installed core.seed_system_roles
            // (V052), which grants core.leave.* to every new tenant; those codes arrive with V025.
            if (tableExists(conn, "reference", "action")
                    && tableExists(conn, "core", "role_action")
                    && !actionExists(conn, "core.leave.apply")) {
                executeResource(conn, "db/migration/core/V025__catalogue_correction.sql");
            }
            if (!columnExists(conn, "core", "tenant", "country_code")) {
                executeResource(conn, "db/migration/core/V033__tenant_locale_columns.sql");
            }
            if (!tableExists(conn, "core", "subscription")) {
                executeResource(conn, "db/migration/core/V034__subscription.sql");
            }
            // WorkingDayBasisCalculator reads the tenant's default holiday calendar.
            if (!tableExists(conn, "core", "work_location")) {
                executeResource(conn, "db/migration/core/V013__work_location.sql");
            }
            if (!tableExists(conn, "core", "holiday_calendar")) {
                executeResource(conn, "db/migration/core/V036__holiday_calendar.sql");
            }
            if (!tableExists(conn, "core", "lop_policy")) {
                executeResource(conn, "db/migration/core/V116__lop_policy.sql");
            }
            if (!constraintExists(conn, "uk_lop_policy_tenant_effective_from")) {
                executeResource(conn, "db/migration/core/V119__lop_policy_unique_effective_from.sql");
            }
            if (!tableExists(conn, "payroll", "pay_schedule")) {
                executeResource(conn, "db/migration/payroll/V054__pay_schedule.sql");
            }
        }
    }

    public static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Tenant Alpha W28");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Tenant Beta W28");
            ps.executeUpdate();
        }
        // Seed default lop_policy for both tenants (W-18.1)
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                    INSERT INTO core.lop_policy (id, tenant_id, working_day_basis, weekends_payable,
                        holidays_payable, lop_rounding, effective_from)
                    SELECT gen_random_uuid(), t.tenant_id, 'ORG_DAYS', false, false, 'HALF_UP_2', '1900-01-01'
                    FROM core.tenant t
                    WHERE t.tenant_id IN ('%s', '%s')
                      AND NOT EXISTS (SELECT 1 FROM core.lop_policy p WHERE p.tenant_id = t.tenant_id)
                    """
                            .formatted(TENANT_A, TENANT_B));
        }
    }

    public static void clearSchedules() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "payroll", "pay_schedule")) {
                stmt.execute(
                        "DELETE FROM payroll.pay_schedule WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean constraintExists(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_constraint WHERE conname = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean actionExists(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM reference.action WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String schema, String table, String column)
            throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, schema, table, column)) {
            return rs.next();
        }
    }

    private static void executeResource(Connection conn, String path) throws Exception {
        ClassLoader cl = PayScheduleTestSchema.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + path);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
