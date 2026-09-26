package com.infinevo.core.authz;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Schema, seed data and owner-level reads for the W-11.1 integration tests — against a database of
 * their own.
 *
 * <p><strong>Why a separate database.</strong> {@code V022__role_action.sql} puts a trigger on
 * {@code core.tenant} that gives every new tenant seven roles, and those roles reference the tenant.
 * Applied to the shared {@code infinevo} database, it would seed roles for the tenants the org and
 * employee suites insert, and {@code JobStatusTenantIT}'s {@code DELETE FROM core.tenant} would then
 * fail on the foreign key — a failure in a test that has nothing to do with roles, depending on the
 * order the classes happen to run in. So these tests provision {@value #DATABASE} on the same
 * container ({@link PostgresTestContainerInitializer#provisionAdditionalDatabase}, which runs the
 * canonical schema and grant scripts there) and nothing else ever sees the trigger.
 *
 * <p>The shipped scripts are applied as written, in version order — {@code V001} (tenant),
 * {@code V002} (user_tenant), {@code V009} (user_account), {@code V010}-{@code V014} (employee and the
 * org masters, for W-11.2's HTTP test), {@code V020} (the catalogue), {@code V021}-{@code V023}, {@code V025} (the catalogue correction) — so the
 * tables under test are the migrated ones and not copies that drifted.
 *
 * <p>Two connections, as in {@code OrgTestSchema}: {@link #migrationConnection()} is the schema owner
 * and bypasses row-level security; {@link #appConnection()} is {@code app_user}, which does not.
 */
public final class AuthzTestSchema {

    static final String DATABASE = "infinevo_authz";

    /** The seven roles {@code core.seed_system_roles} gives every tenant — spec section 13, decision 2. */
    public static final Set<String> SYSTEM_ROLES =
            Set.of("platform-admin", "tenant-admin", "hr", "manager", "payroll-officer", "finance", "employee");

    private static String jdbcUrl;

    private AuthzTestSchema() {}

    /** Points the Spring datasource at {@value #DATABASE}. Listed after {@link PostgresTestContainerInitializer}. */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of("spring.datasource.url=" + jdbcUrl()).applyTo(ctx.getEnvironment());
        }
    }

    /** The database's URL, provisioning it and applying the scripts on first call. */
    public static synchronized String jdbcUrl() {
        if (jdbcUrl == null) {
            String url = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
            try (Connection conn = DriverManager.getConnection(
                    url,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                if (!tableExists(conn, "core", "role")) {
                    executeResource(conn, "db/migration/core/V001__tenant.sql");
                    // W-11.2: PermissionGuardIT goes through TenantContextFilter, which checks
                    // membership in core.user_tenant, and reaches the employee endpoints, whose
                    // queries name the V014 org columns.
                    executeResource(conn, "db/migration/core/V002__user_tenant.sql");
                    // W-12.3: the real AuditController answers the core.audit menu item in the guard tests
                    executeResource(conn, "db/migration/core/V008__audit_log.sql");
                    executeResource(conn, "db/migration/core/V009__user_account.sql");
                    executeResource(conn, "db/migration/core/V010__employee.sql");
                    executeResource(conn, "db/migration/core/V011__department.sql");
                    executeResource(conn, "db/migration/core/V012__designation.sql");
                    executeResource(conn, "db/migration/core/V013__work_location.sql");
                    executeResource(conn, "db/migration/core/V014__employee_org_columns.sql");
                    executeResource(conn, "db/migration/core/V015__employee_personal.sql");
                    executeResource(conn, "db/migration/core/V016__employee_contact.sql");
                    executeResource(conn, "db/migration/reference/V020__action.sql");
                    executeResource(conn, "db/migration/core/V021__role.sql");
                    executeResource(conn, "db/migration/core/V022__role_action.sql");
                    executeResource(conn, "db/migration/core/V023__user_role.sql");
                    // W-11.3: the catalogue correction — core.* leave, attendance and holiday codes,
                    // and no core.tenant.provision on any tenant-seeded role.
                    executeResource(conn, "db/migration/core/V025__catalogue_correction.sql");
                    // W-12.1: subscription and tenant locale columns
                    executeResource(conn, "db/migration/core/V033__tenant_locale_columns.sql");
                    executeResource(conn, "db/migration/core/V034__subscription.sql");
                    // W-13.4: user_account_id FK on core.employee; PermissionGuardIT reaches the
                    // employee endpoint so Hibernate selects this column.
                    executeResource(conn, "db/migration/core/V026__employee_user_account.sql");
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

    static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    /**
     * Inserts a fresh tenant as the schema owner — the way the dev seed does — so the trigger, not a
     * hand call to the seed function, is what gives it its roles. Random id: the database is shared by
     * both test classes and no test cleans another's rows.
     */
    public static UUID insertTenant(String name) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
        return tenantId;
    }

    /** Inserts a user account in a tenant, as the schema owner. */
    public static UUID insertUserAccount(UUID tenantId, String email) throws SQLException {
        return insertUserAccount(tenantId, UUID.randomUUID(), email);
    }

    /**
     * A member of the tenant as a request sees one: a {@code core.user_tenant} row, which
     * {@code TenantContextFilter} checks, and a {@code core.user_account} profile, which the permission
     * check resolves the token subject to. Returns the profile row's id.
     */
    public static UUID insertMember(UUID tenantId, UUID keycloakUserId, String email) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.user_tenant (tenant_id, user_id) VALUES (?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, keycloakUserId);
            ps.executeUpdate();
        }
        return insertUserAccount(tenantId, keycloakUserId, email);
    }

    private static UUID insertUserAccount(UUID tenantId, UUID keycloakUserId, String email) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by)
                        VALUES (?, ?, ?, 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, keycloakUserId);
            ps.setString(3, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** Inserts a tenant's own, non-system role holding the given actions, as the schema owner. */
    public static UUID insertRole(UUID tenantId, String code, String name, String... actionCodes) throws SQLException {
        try (Connection conn = migrationConnection()) {
            UUID roleId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.role (tenant_id, code, name) VALUES (?, ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, code);
                ps.setString(3, name);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    roleId = rs.getObject(1, UUID.class);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES (?, ?, ?)")) {
                for (String actionCode : actionCodes) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, roleId);
                    ps.setString(3, actionCode);
                    ps.executeUpdate();
                }
            }
            return roleId;
        }
    }

    /** Grants a role to a user, as the schema owner. */
    public static void grant(UUID tenantId, UUID userAccountId, UUID roleId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userAccountId);
            ps.setObject(3, roleId);
            ps.executeUpdate();
        }
    }

    /** The id of a tenant's role by code, read as the schema owner. */
    public static UUID roleId(UUID tenantId, String code) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT id FROM core.role WHERE tenant_id = ? AND code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no role " + code + " in tenant " + tenantId);
                }
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** The action codes a role holds, read as the schema owner — the control for the RLS assertions. */
    public static Set<String> actionsOfRole(UUID roleId) throws SQLException {
        return strings("SELECT action_code FROM core.role_action WHERE role_id = ?", roleId);
    }

    /** The role ids a user holds, read as the schema owner. */
    static Set<String> rolesOfUser(UUID userAccountId) throws SQLException {
        return strings("SELECT role_id::text FROM core.user_role WHERE user_account_id = ?", userAccountId);
    }

    /** One column of one row of a {@code core} table, read as the schema owner. */
    static Object readColumn(String table, UUID id, String column) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT " + column + " FROM core." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        }
    }

    static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    private static Set<String> strings(String sql, UUID param) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            Set<String> out = new LinkedHashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
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
        try (InputStream is = AuthzTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
