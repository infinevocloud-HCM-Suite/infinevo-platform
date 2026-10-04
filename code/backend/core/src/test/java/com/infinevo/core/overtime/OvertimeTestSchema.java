package com.infinevo.core.overtime;

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
 * The database and fixtures behind overtime's integration tests (W-39.2) — {@code PayInputTestSchema}'s
 * shape, plus {@code V041} for {@code core.overtime_request}.
 */
final class OvertimeTestSchema {

    static final String DATABASE = "infinevo_overtime";

    private static String jdbcUrl;

    private OvertimeTestSchema() {}

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
                if (!tableExists(conn, "overtime_request")) {
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
                        "core/V026__employee_user_account.sql",
                        "core/V031__pay_input.sql",
                        "core/V032__pay_input_period_lock.sql",
                        "core/V060__pay_input_run_ref.sql",
                        "core/V041__overtime_request.sql",
                        "core/V120__overtime_request_states.sql",
                        // W-68: the current core.seed_system_roles (V148) grants codes these add.
                        "reference/V052__fbp_actions.sql",
                        "reference/V097__reimbursement_claim_actions.sql",
                        "reference/V100__employee_deduction_actions.sql",
                        "core/V085__hrms_project_actions.sql",
                        "reference/V122__hrms_request_actions.sql",
                        "core/V148__overtime_read_seed_roles.sql"
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

    static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    static UUID insertTenant(String name) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        update("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)", tenantId, name);
        return tenantId;
    }

    /** An active employee. Returns its id. */
    static UUID insertEmployee(UUID tenantId, String number) throws SQLException {
        return uuid(
                "INSERT INTO core.employee (tenant_id, employee_number, first_name, work_email, date_of_joining,"
                        + " status) VALUES (?, ?, 'Test', ?, DATE '2026-04-01', 'ACTIVE') RETURNING id",
                tenantId,
                number,
                number.toLowerCase() + "@overtime.test");
    }

    /** A soft-deleted employee — {@code OvertimeServiceImpl} must refuse an entry against one. */
    static UUID insertDeletedEmployee(UUID tenantId, String number) throws SQLException {
        UUID id = insertEmployee(tenantId, number);
        update("UPDATE core.employee SET is_deleted = true WHERE id = ?", id);
        return id;
    }

    static void insertMember(UUID tenantId, UUID sub, String roleCode) throws SQLException {
        UUID accountId = insertUserAccount(tenantId, sub);
        UUID roleId = uuid("SELECT id FROM core.role WHERE tenant_id = ? AND code = ?", tenantId, roleCode);
        assignRole(tenantId, accountId, roleId);
    }

    /** A member holding a role created just for this test, with exactly the actions named. */
    static void insertMemberWithActions(UUID tenantId, UUID sub, String... actionCodes) throws SQLException {
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
                "INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by)"
                        + " VALUES (?, ?, ?, 'test', 'test') RETURNING id",
                tenantId,
                sub,
                sub + "@overtime.test");
    }

    private static void assignRole(UUID tenantId, UUID accountId, UUID roleId) throws SQLException {
        update(
                "INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES (?, ?, ?)",
                tenantId,
                accountId,
                roleId);
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

    /** Runs one migration script as the migration user - W-68's backfill is re-run by {@code OvertimeSeedRolesIT}. */
    static void runMigration(String script) throws Exception {
        try (Connection conn = migrationConnection()) {
            executeResource(conn, "db/migration/" + script);
        }
    }

    private static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = OvertimeTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("migration not on the test classpath: " + resourcePath);
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
