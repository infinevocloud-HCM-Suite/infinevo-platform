package com.infinevo.hrms.project;

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
 * Database schema and test fixture setup for HRMS project integration tests (W-41).
 */
public final class HrmsProjectTestSchema {

    public static final String DATABASE = "infinevo_hrms_project";

    private static String jdbcUrl;

    private HrmsProjectTestSchema() {}

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
                if (!tableExists(conn, "hrms", "project")) {
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
                        "core/V026__employee_user_account.sql",
                        "reference/V052__fbp_actions.sql",
                        "reference/V097__reimbursement_claim_actions.sql",
                        "reference/V100__employee_deduction_actions.sql",
                        "core/V085__hrms_project_actions.sql",
                        "core/V135__hrms_project_seed_roles.sql",
                        "hrms/V086__project.sql",
                        "hrms/V087__task.sql",
                        "hrms/V088__assignment.sql"
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
        update("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)", tenantId, name);
        return tenantId;
    }

    public static UUID insertEmployee(UUID tenantId, String number) throws SQLException {
        return uuid(
                "INSERT INTO core.employee (tenant_id, employee_number, first_name, work_email, date_of_joining, status) "
                        + "VALUES (?, ?, 'Test', ?, DATE '2026-04-01', 'ACTIVE') RETURNING id",
                tenantId,
                number,
                number.toLowerCase() + "@hrms.test");
    }

    public static void insertMember(UUID tenantId, UUID sub, String roleCode) throws SQLException {
        UUID accountId = insertUserAccount(tenantId, sub);
        UUID roleId = uuid("SELECT id FROM core.role WHERE tenant_id = ? AND code = ?", tenantId, roleCode);
        assignRole(tenantId, accountId, roleId);
    }

    public static void insertMemberWithActions(UUID tenantId, UUID sub, String... actionCodes) throws SQLException {
        UUID accountId = insertUserAccount(tenantId, sub);
        UUID roleId = uuid(
                "INSERT INTO core.role (tenant_id, code, name) VALUES (?, ?, ?) RETURNING id",
                tenantId,
                "test-role-" + sub,
                "Test role for " + sub);
        for (String actionCode : actionCodes) {
            update(
                    "INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES (?, ?, ?)",
                    tenantId,
                    roleId,
                    actionCode);
        }
        assignRole(tenantId, accountId, roleId);
    }

    private static UUID insertUserAccount(UUID tenantId, UUID sub) throws SQLException {
        update("INSERT INTO core.user_tenant (tenant_id, user_id) VALUES (?, ?)", tenantId, sub);
        return uuid(
                "INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by) "
                        + "VALUES (?, ?, ?, 'test', 'test') RETURNING id",
                tenantId,
                sub,
                sub + "@hrms.test");
    }

    private static void assignRole(UUID tenantId, UUID accountId, UUID roleId) throws SQLException {
        update(
                "INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES (?, ?, ?)",
                tenantId,
                accountId,
                roleId);
    }

    public static long count(String sql, Object... params) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public static void update(String sql, Object... params) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    public static UUID uuid(String sql, Object... params) throws SQLException {
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

    private static boolean tableExists(Connection conn, String schema, String table) throws SQLException {
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
        try (InputStream is = HrmsProjectTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("migration not on the test classpath: " + resourcePath);
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
