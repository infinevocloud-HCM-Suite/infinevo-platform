package com.infinevo.core.report;

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
import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The database, storage and fixtures behind the export integration tests (W-23.1) — a database of
 * its own, {@value #DATABASE}, for the reason {@code DocumentTestSchema} gives.
 *
 * <p>The shipped scripts, run as the owner: identity and roles for the permission check, the employee
 * and the org masters and the audit log for the three sources, the document store for the file, and
 * {@code V040}, whose trigger seeds each new tenant's three system definitions.
 */
final class ReportTestSchema {

    static final String DATABASE = "infinevo_report";
    static final String LINK_SECRET = "integration-test-export-link-secret-000";

    private static String jdbcUrl;

    private ReportTestSchema() {}

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                            "spring.datasource.url=" + jdbcUrl(),
                            "document.link.secret=" + LINK_SECRET,
                            "document.blob.connection-string=" + AzuriteTestContainer.connectionString())
                    .applyTo(ctx.getEnvironment());
        }
    }

    static synchronized String jdbcUrl() {
        if (jdbcUrl == null) {
            String url = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
            try (Connection conn = DriverManager.getConnection(
                    url,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                if (!tableExists(conn, "report_definition")) {
                    for (String script : new String[] {
                        "core/V001__tenant.sql",
                        "core/V002__user_tenant.sql",
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
                        "core/V037__document.sql",
                        "core/V040__report_definition.sql"
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

    /** A fresh tenant. The triggers seed its seven system roles and three system definitions. */
    static UUID insertTenant(String name) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        update("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)", tenantId, name);
        return tenantId;
    }

    /** A member holding one existing role, by code. Returns the user_account id. */
    static UUID insertMember(UUID tenantId, UUID sub, String roleCode) throws SQLException {
        update("INSERT INTO core.user_tenant (tenant_id, user_id) VALUES (?, ?)", tenantId, sub);
        UUID accountId = uuid(
                "INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by)"
                        + " VALUES (?, ?, ?, 'test', 'test') RETURNING id",
                tenantId,
                sub,
                sub + "@report.test");
        UUID roleId = uuid("SELECT id FROM core.role WHERE tenant_id = ? AND code = ?", tenantId, roleCode);
        update(
                "INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES (?, ?, ?)",
                tenantId,
                accountId,
                roleId);
        return accountId;
    }

    /** A tenant role holding exactly {@code actions}, for the second-gate cases. */
    static void insertRole(UUID tenantId, String code, String... actions) throws SQLException {
        UUID roleId = uuid(
                "INSERT INTO core.role (tenant_id, code, name) VALUES (?, ?, ?) RETURNING id", tenantId, code, code);
        for (String action : actions) {
            update(
                    "INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES (?, ?, ?)",
                    tenantId,
                    roleId,
                    action);
        }
    }

    static void insertEmployee(UUID tenantId, String number, String status) throws SQLException {
        update(
                "INSERT INTO core.employee (tenant_id, employee_number, first_name, date_of_joining, status,"
                        + " termination_date) VALUES (?, ?, 'Test', DATE '2026-04-01', ?,"
                        + " CASE WHEN ? = 'TERMINATED' THEN DATE '2026-09-01' END)",
                tenantId,
                number,
                status,
                status);
    }

    /** {@code count} active employees in one statement — for the 10,000-row case. */
    static void insertEmployees(UUID tenantId, int count) throws SQLException {
        update(
                "INSERT INTO core.employee (tenant_id, employee_number, first_name, date_of_joining, status)"
                        + " SELECT ?, 'BULK-' || lpad(n::text, 6, '0'), 'Bulk', DATE '2026-04-01', 'ACTIVE'"
                        + " FROM generate_series(1, ?) AS n",
                tenantId,
                count);
    }

    static void insertDepartment(UUID tenantId, String code) throws SQLException {
        update("INSERT INTO core.department (tenant_id, code, name) VALUES (?, ?, ?)", tenantId, code, code + " name");
    }

    static void insertAuditRow(UUID tenantId, String table) throws SQLException {
        update(
                "INSERT INTO core.audit_log (tenant_id, actor_label, operation, entity_schema, entity_table, entity_id,"
                        + " changed_columns) VALUES (?, 'test', 'UPDATE', 'core', ?, 'x', ARRAY['status'])",
                tenantId,
                table);
    }

    /** The id of one of the tenant's definitions, by code. */
    static UUID definitionId(UUID tenantId, String code) throws SQLException {
        return uuid("SELECT id FROM core.report_definition WHERE tenant_id = ? AND code = ?", tenantId, code);
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
        try (InputStream is = ReportTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("migration not on the test classpath: " + resourcePath);
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
