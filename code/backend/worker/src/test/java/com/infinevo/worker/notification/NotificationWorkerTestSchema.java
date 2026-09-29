package com.infinevo.worker.notification;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Shared database schema and fixtures for worker notification integration tests (W-20.2).
 * Provisions an isolated test database with the complete schema required by reminder rules,
 * ShedLock, tenants, and notifications.
 */
public final class NotificationWorkerTestSchema {

    public static final String DATABASE = "infinevo_worker_notification";

    private static String jdbcUrl;

    private NotificationWorkerTestSchema() {}

    /**
     * Points the context at this database. It runs after {@link PostgresTestContainerInitializer},
     * which orders itself first, so these values win. {@code DB_URL} as well as the datasource URL:
     * the worker's retention and report-read pools default to {@code DB_URL}.
     */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of("DB_URL=" + jdbcUrl(), "spring.datasource.url=" + jdbcUrl())
                    .applyTo(ctx.getEnvironment());
        }
    }

    public static synchronized String jdbcUrl() {
        if (jdbcUrl == null) {
            String url = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
            try (Connection conn = DriverManager.getConnection(
                    url,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                if (!tableExists(conn, "core", "reminder_rule")) {
                    for (String script : new String[] {
                        "core/V001__tenant.sql",
                        "core/V002__user_tenant.sql",
                        "core/V006__job_status_and_shedlock.sql",
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
                        "core/V026__employee_user_account.sql",
                        "core/V033__tenant_locale_columns.sql",
                        "core/V038__notification_template.sql",
                        "core/V039__notification.sql",
                        "core/V093__reminder_rule.sql"
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

    public static Connection workerConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.WORKER_USER,
                PostgresTestContainerInitializer.WORKER_USER_PASSWORD);
    }

    public static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    public static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    public static UUID insertTenant(String name, String timezone) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name, timezone) VALUES (gen_random_uuid(), ?, ?) RETURNING tenant_id")) {
            ps.setString(1, name);
            ps.setString(2, timezone != null ? timezone : "UTC");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public static UUID insertEmployee(UUID tenantId, String number, String email) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (tenant_id, employee_number, first_name, work_email, date_of_joining, status) "
                                + "VALUES (?, ?, 'Test', ?, DATE '2026-04-01', 'ACTIVE') RETURNING id")) {
            ps.setObject(1, tenantId);
            ps.setString(2, number);
            ps.setString(3, email != null ? email : (number.toLowerCase() + "@infinevo.test"));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public static UUID insertReminderRule(
            UUID tenantId,
            String event,
            String audience,
            String anchor,
            int offsetDays,
            Integer dayOfWeek,
            Time sendAtLocalTime,
            boolean isActive)
            throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO core.reminder_rule ("
                        + "tenant_id, event, audience, anchor, offset_days, day_of_week, send_at_local_time, is_active"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setObject(1, tenantId);
            ps.setString(2, event);
            ps.setString(3, audience);
            ps.setString(4, anchor);
            ps.setInt(5, offsetDays);
            if (dayOfWeek != null) {
                ps.setShort(6, dayOfWeek.shortValue());
            } else {
                ps.setNull(6, java.sql.Types.SMALLINT);
            }
            ps.setTime(7, sendAtLocalTime);
            ps.setBoolean(8, isActive);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public static UUID insertNotification(
            UUID tenantId,
            UUID recipientEmployeeId,
            String recipientEmail,
            String event,
            String channel,
            String subject,
            String body,
            String status)
            throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO core.notification ("
                        + "id, tenant_id, recipient_employee_id, recipient_email, event, channel, subject, body, status, queued_at"
                        + ") VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, clock_timestamp()) RETURNING id")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, recipientEmployeeId);
            ps.setString(3, recipientEmail);
            ps.setString(4, event);
            ps.setString(5, channel);
            ps.setString(6, subject);
            ps.setString(7, body);
            ps.setString(8, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public static boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = ? AND tablename = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void executeResource(Connection conn, String resourcePath) throws Exception {
        String sql = readSqlResource(resourcePath);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public static String readSqlResource(String resourcePath) throws Exception {
        try (InputStream is =
                NotificationWorkerTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        // Fallback to project filesystem paths
        Path[] candidatePaths = new Path[] {
            Path.of("../migration/src/main/resources").resolve(resourcePath),
            Path.of("migration/src/main/resources").resolve(resourcePath),
            Path.of("code/backend/migration/src/main/resources").resolve(resourcePath),
            Path.of("../../migration/src/main/resources").resolve(resourcePath)
        };
        for (Path path : candidatePaths) {
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("SQL resource not found on classpath or filesystem: " + resourcePath);
    }
}
