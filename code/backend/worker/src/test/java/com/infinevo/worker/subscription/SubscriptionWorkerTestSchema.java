package com.infinevo.worker.subscription;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class SubscriptionWorkerTestSchema {

    public static final String DATABASE = "infinevo_worker_subscription";

    private static String jdbcUrl;

    private SubscriptionWorkerTestSchema() {}

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
                if (!tableExists(conn, "core", "subscription")) {
                    for (String script : new String[] {
                        "core/V001__tenant.sql",
                        "core/V002__user_tenant.sql",
                        "core/V006__job_status_and_shedlock.sql",
                        "core/V008__audit_log.sql",
                        "core/V009__user_account.sql",
                        "core/V010__employee.sql",
                        "core/V173__employee_termination_reason.sql",
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
                        "core/V034__subscription.sql",
                        "core/V117__user_invitation.sql",
                        "core/V138__subscription_status_check.sql",
                        "core/V188__subscription_plan.sql",
                        "core/V208__subscription_period_end.sql"
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

    public static UUID insertTenant(String name) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name, country_code, timezone) VALUES (?, ?, 'IN', 'Asia/Kolkata')")) {
            ps.setObject(1, id);
            ps.setString(2, name);
            ps.executeUpdate();
        }
        return id;
    }

    public static void insertSubscription(UUID tenantId, String status, LocalDate periodEnd) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.subscription (tenant_id, status, started_on, current_period_end)
                        VALUES (?, ?, now(), ?)
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, status);
            if (periodEnd != null) {
                ps.setDate(3, java.sql.Date.valueOf(periodEnd));
            } else {
                ps.setNull(3, java.sql.Types.DATE);
            }
            ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, schema, table, new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is =
                SubscriptionWorkerTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("migration not on the test classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
