package com.infinevo.hrms.attendance;

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
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Database schema and test fixture setup for HRMS attendance preferences integration tests (W-40.1).
 */
public final class HrmsAttendanceTestSchema {

    public static final String DATABASE = "infinevo_hrms_attendance";

    private static String jdbcUrl;

    private HrmsAttendanceTestSchema() {}

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of("spring.datasource.url=" + jdbcUrl()).applyTo(ctx.getEnvironment());
        }
    }

    public static synchronized String jdbcUrl() {
        if (jdbcUrl == null) {
            String url = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
            try (Connection conn = DriverManager.getConnection(
                    url,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                if (!tableExists(conn, "hrms", "attendance_preference")) {
                    for (String script : new String[] {
                        "core/V001__tenant.sql",
                        "core/V002__user_tenant.sql",
                        "core/V008__audit_log.sql",
                        "core/V009__user_account.sql",
                        "core/V010__employee.sql",
                        "reference/V020__action.sql",
                        "core/V021__role.sql",
                        "core/V022__role_action.sql",
                        "core/V023__user_role.sql",
                        "core/V025__catalogue_correction.sql",
                        "hrms/V121__attendance_preference.sql"
                    }) {
                        executeResource(conn, "db/migration/" + script);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not prepare " + DATABASE, e);
            }
            jdbcUrl = url;
        }
        return jdbcUrl;
    }

    public static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    public static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    public static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    public static UUID insertTenant(String name) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name, country_code, status) VALUES (?, ?, 'IND', 'ACTIVE')")) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
        return tenantId;
    }

    public static void insertMember(UUID tenantId, UUID userAccountId, String roleName) throws SQLException {
        try (Connection conn = migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.user_account (id, email, password_hash, status) VALUES (?, ?, 'hash', 'ACTIVE') "
                            + "ON CONFLICT (id) DO NOTHING")) {
                ps.setObject(1, userAccountId);
                ps.setString(2, userAccountId + "@hrms.test");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.user_tenant (user_account_id, tenant_id, is_active) VALUES (?, ?, true) "
                            + "ON CONFLICT DO NOTHING")) {
                ps.setObject(1, userAccountId);
                ps.setObject(2, tenantId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.user_role (user_account_id, tenant_id, role_name) VALUES (?, ?, ?) "
                            + "ON CONFLICT DO NOTHING")) {
                ps.setObject(1, userAccountId);
                ps.setObject(2, tenantId);
                ps.setString(3, roleName);
                ps.executeUpdate();
            }
        }
    }

    public static int countAuditRows(UUID tenantId, String entityTable) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.audit_log WHERE tenant_id = ? AND entity_table = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, entityTable);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

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

    private static void executeResource(Connection conn, String path) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Migration resource not found: " + path);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
    }
}
