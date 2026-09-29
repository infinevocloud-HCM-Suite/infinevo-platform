package com.infinevo.worker.retention;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Shared database schema and fixtures for worker retention integration tests (W-22.2).
 */
public final class RetentionWorkerTestSchema {

    public static final String DATABASE = "infinevo_worker_retention";

    private static String jdbcUrl;

    private RetentionWorkerTestSchema() {}

    /**
     * Points the context at this database. It runs after {@link PostgresTestContainerInitializer},
     * which orders itself first, so these values win. {@code DB_URL} as well as the datasource URL:
     * the worker's retention and report-read pools default to {@code DB_URL}.
     */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                            "DB_URL=" + jdbcUrl(),
                            "spring.datasource.url=" + jdbcUrl(),
                            "worker.retention.datasource.url=" + jdbcUrl(),
                            "worker.retention.datasource.username=" + PostgresTestContainerInitializer.RETENTION_USER,
                            "worker.retention.datasource.password="
                                    + PostgresTestContainerInitializer.RETENTION_USER_PASSWORD)
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
                if (!tableExists(conn, "core", "retention_run")) {
                    for (String script : new String[] {
                        "core/V001__tenant.sql",
                        "core/V002__user_tenant.sql",
                        "core/V006__job_status_and_shedlock.sql",
                        "core/V008__audit_log.sql",
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
                        "core/V093__reminder_rule.sql",
                        "core/V094__retention_run.sql"
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

    public static Connection retentionConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.RETENTION_USER,
                PostgresTestContainerInitializer.RETENTION_USER_PASSWORD);
    }

    public static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    public static void insertTenant(
            Connection conn, UUID tenantId, String name, int auditRetentionMonths, int notificationRetentionMonths)
            throws SQLException {
        String sql = "INSERT INTO core.tenant ("
                + "tenant_id, name, country_code, audit_retention_months, notification_retention_months"
                + ") VALUES (?, ?, 'IN', ?, ?) "
                + "ON CONFLICT (tenant_id) DO UPDATE SET "
                + "audit_retention_months = EXCLUDED.audit_retention_months, "
                + "notification_retention_months = EXCLUDED.notification_retention_months";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.setInt(3, auditRetentionMonths);
            ps.setInt(4, notificationRetentionMonths);
            ps.executeUpdate();
        }
    }

    public static UUID insertAuditLog(Connection conn, UUID tenantId, String table, String entityId, Instant occurredAt)
            throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO core.audit_log ("
                + "id, tenant_id, entity_schema, entity_table, entity_id, operation, actor_label, occurred_at"
                + ") VALUES (?, ?, 'core', ?, ?, 'INSERT', 'test_user', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, table);
            ps.setString(4, entityId);
            ps.setTimestamp(5, Timestamp.from(occurredAt));
            ps.executeUpdate();
        }
        return id;
    }

    public static UUID insertNotification(Connection conn, UUID tenantId, Instant queuedAt) throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO core.notification ("
                + "id, tenant_id, event, channel, recipient_email, body, status, queued_at"
                + ") VALUES (?, ?, 'test_event', 'EMAIL', 'test@example.com', 'Test Body', 'SENT', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setTimestamp(3, Timestamp.from(queuedAt));
            ps.executeUpdate();
        }
        return id;
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

    private static void executeResource(Connection conn, String resourcePath) {
        try (InputStream is = RetentionWorkerTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Missing test migration resource: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute " + resourcePath, e);
        }
    }
}
