package com.infinevo.worker.report;

import com.infinevo.shared.test.AzuriteTestContainer;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Shared database schema and fixtures for worker report integration tests (W-23.2).
 */
public final class ReportWorkerTestSchema {

    public static final String DATABASE = "infinevo_worker_report";

    private static String jdbcUrl;

    private ReportWorkerTestSchema() {}

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
                            "worker.report.datasource.url=" + jdbcUrl(),
                            "worker.report.datasource.username=" + PostgresTestContainerInitializer.READONLY_USER,
                            "worker.report.datasource.password="
                                    + PostgresTestContainerInitializer.READONLY_USER_PASSWORD,
                            // An export is stored as a document (W-21): without a store every run fails.
                            "document.blob.connection-string=" + AzuriteTestContainer.connectionString())
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
                if (!tableExists(conn, "core", "report_schedule")) {
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
                        "core/V037__document.sql",
                        "core/V166__document_label.sql",
                        "core/V038__notification_template.sql",
                        "core/V039__notification.sql",
                        "core/V040__report_definition.sql",
                        "core/V093__reminder_rule.sql",
                        "core/V095__report_schedule.sql",
                        "core/V096__scheduled_report_notification.sql"
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

    public static Connection readonlyConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.READONLY_USER,
                PostgresTestContainerInitializer.READONLY_USER_PASSWORD);
    }

    public static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    public static void insertTenant(Connection conn, UUID tenantId, String name, String timezone) throws SQLException {
        String sql = "INSERT INTO core.tenant ("
                + "tenant_id, name, country_code, timezone"
                + ") VALUES (?, ?, 'IN', ?) "
                + "ON CONFLICT (tenant_id) DO UPDATE SET "
                + "name = EXCLUDED.name, "
                + "timezone = EXCLUDED.timezone";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.setString(3, timezone != null ? timezone : "UTC");
            ps.executeUpdate();
        }
    }

    public static void insertReportDefinition(
            Connection conn,
            UUID id,
            UUID tenantId,
            String code,
            String name,
            String source,
            String columnsJson,
            String requiredAction,
            String format)
            throws SQLException {
        String sql = "INSERT INTO core.report_definition ("
                + "id, tenant_id, code, name, source, columns, default_filters, format, required_action, is_system"
                + ") VALUES (?, ?, ?, ?, ?, ?::jsonb, '{}'::jsonb, ?, ?, false) "
                + "ON CONFLICT (tenant_id, code) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, name);
            ps.setString(5, source);
            ps.setString(6, columnsJson);
            ps.setString(7, format != null ? format : "CSV");
            ps.setString(8, requiredAction != null ? requiredAction : "core.report.read");
            ps.executeUpdate();
        }
    }

    public static void insertReportSchedule(
            Connection conn,
            UUID id,
            UUID tenantId,
            UUID definitionId,
            String cadence,
            Integer dayOfPeriod,
            LocalTime sendAtLocalTime,
            String recipientEmails,
            boolean isActive)
            throws SQLException {
        insertReportSchedule(
                conn,
                id,
                tenantId,
                definitionId,
                cadence,
                dayOfPeriod,
                sendAtLocalTime,
                recipientEmails,
                isActive,
                null);
    }

    /**
     * With an explicit owner (manager's review item B-4) — needed by any test that runs the row
     * through {@code ReportScheduleEvaluator.executeSchedule}, which refuses a schedule whose owner
     * cannot be shown to hold the definition's {@code required_action} right now.
     */
    public static void insertReportSchedule(
            Connection conn,
            UUID id,
            UUID tenantId,
            UUID definitionId,
            String cadence,
            Integer dayOfPeriod,
            LocalTime sendAtLocalTime,
            String recipientEmails,
            boolean isActive,
            UUID ownerUserAccountId)
            throws SQLException {
        String sql = "INSERT INTO core.report_schedule ("
                + "id, tenant_id, definition_id, cadence, day_of_period, send_at_local_time, recipient_emails,"
                + " is_active, owner_user_account_id"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET is_active = EXCLUDED.is_active,"
                + " owner_user_account_id = EXCLUDED.owner_user_account_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, definitionId);
            ps.setString(4, cadence);
            if (dayOfPeriod != null) {
                ps.setInt(5, dayOfPeriod);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setTime(6, Time.valueOf(sendAtLocalTime));
            ps.setString(7, recipientEmails);
            ps.setBoolean(8, isActive);
            ps.setObject(9, ownerUserAccountId);
            ps.executeUpdate();
        }
    }

    /**
     * A {@code tenant-admin} account, which {@code core.seed_system_roles} grants every action except
     * {@code core.tenant.provision} — so it holds any {@code required_action} a test definition names.
     * Returns its {@code user_account.id}, to store as a schedule's owner.
     */
    public static UUID insertTenantAdminAccount(Connection conn, UUID tenantId) throws SQLException {
        UUID sub = UUID.randomUUID();
        try (PreparedStatement ps =
                conn.prepareStatement("INSERT INTO core.user_tenant (tenant_id, user_id) VALUES (?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, sub);
            ps.executeUpdate();
        }
        UUID accountId;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by)"
                        + " VALUES (?, ?, ?, 'test', 'test') RETURNING id")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, sub);
            ps.setString(3, sub + "@report-owner.test");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                accountId = rs.getObject(1, UUID.class);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.user_role (tenant_id, user_account_id, role_id) SELECT ?, ?, id FROM core.role"
                        + " WHERE tenant_id = ? AND code = 'tenant-admin'")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, accountId);
            ps.setObject(3, tenantId);
            ps.executeUpdate();
        }
        return accountId;
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
        ClassLoader cl = ReportWorkerTestSchema.class.getClassLoader();
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
