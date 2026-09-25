package com.infinevo.core.notification;

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
 * The database and fixtures behind the notification integration tests (W-20.1) — a database of its
 * own, {@value #DATABASE}, for the reason {@code DocumentTestSchema} gives.
 *
 * <p>The shipped scripts, run as the owner: identity and roles for the permission check, the employee
 * a notification is addressed to, and {@code V038}/{@code V039}, whose trigger seeds each new tenant's
 * thirty-two default templates.
 */
final class NotificationTestSchema {

    static final String DATABASE = "infinevo_notification";

    private static String jdbcUrl;

    private NotificationTestSchema() {}

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of("spring.datasource.url=" + jdbcUrl()).applyTo(ctx.getEnvironment());
        }
    }

    static synchronized String jdbcUrl() {
        if (jdbcUrl == null) {
            String url = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
            try (Connection conn = DriverManager.getConnection(
                    url,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                if (!tableExists(conn, "notification")) {
                    for (String script : new String[] {
                        "core/V001__tenant.sql",
                        "core/V002__user_tenant.sql",
                        "core/V009__user_account.sql",
                        "core/V010__employee.sql",
                        "core/V011__department.sql",
                        "core/V012__designation.sql",
                        "core/V013__work_location.sql",
                        "core/V014__employee_org_columns.sql",
                        "reference/V020__action.sql",
                        "core/V021__role.sql",
                        "core/V022__role_action.sql",
                        "core/V023__user_role.sql",
                        "core/V025__catalogue_correction.sql",
                        "core/V038__notification_template.sql",
                        "core/V039__notification.sql"
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

    static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    /** Binds {@code conn}'s open transaction to a tenant, as the proxy does for the app's connections. */
    static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    /** A fresh tenant. The triggers seed its seven system roles and its default templates. */
    static UUID insertTenant(String name) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        update("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)", tenantId, name);
        return tenantId;
    }

    /** A member holding one existing role, by code. */
    static void insertMember(UUID tenantId, UUID sub, String roleCode) throws SQLException {
        update("INSERT INTO core.user_tenant (tenant_id, user_id) VALUES (?, ?)", tenantId, sub);
        UUID accountId = uuid(
                "INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by)"
                        + " VALUES (?, ?, ?, 'test', 'test') RETURNING id",
                tenantId,
                sub,
                sub + "@notification.test");
        UUID roleId = uuid("SELECT id FROM core.role WHERE tenant_id = ? AND code = ?", tenantId, roleCode);
        update(
                "INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES (?, ?, ?)",
                tenantId,
                accountId,
                roleId);
    }

    /** An active employee with a work email. Returns its id. */
    static UUID insertEmployee(UUID tenantId, String number) throws SQLException {
        return uuid(
                "INSERT INTO core.employee (tenant_id, employee_number, first_name, work_email, date_of_joining,"
                        + " status) VALUES (?, ?, 'Test', ?, DATE '2026-04-01', 'ACTIVE') RETURNING id",
                tenantId,
                number,
                number.toLowerCase() + "@notification.test");
    }

    static long count(String sql, Object... params) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void update(String sql, Object... params) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static UUID uuid(String sql, Object... params) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no row for: " + sql);
                }
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
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
        try (InputStream is = NotificationTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("migration not on the test classpath: " + resourcePath);
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
