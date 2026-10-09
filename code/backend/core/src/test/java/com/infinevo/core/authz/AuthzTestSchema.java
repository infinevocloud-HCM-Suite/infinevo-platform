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
                    if (!tableExists(conn, "core", "reporting_line")) {
                        executeResource(conn, "db/migration/core/V028__reporting_line.sql");
                    }
                    if (!tableExists(conn, "core", "approval_definition")) {
                        executeResource(conn, "db/migration/core/V089__approval_definition.sql");
                    }
                    if (!tableExists(conn, "core", "approval_instance")) {
                        executeResource(conn, "db/migration/core/V090__approval_instance.sql");
                    }
                    if (!tableExists(conn, "core", "approval_step")) {
                        executeResource(conn, "db/migration/core/V091__approval_step.sql");
                    }
                    if (!tableExists(conn, "core", "approval_delegation")) {
                        executeResource(conn, "db/migration/core/V092__approval_delegation.sql");
                    }
                    if (!tableExists(conn, "core", "attendance")) {
                        executeResource(conn, "db/migration/core/V030__attendance.sql");
                    }
                    // V085 rewrites core.seed_system_roles with the full current grant list, which
                    // includes the payroll.fbp.* codes only V052 inserts into reference.action. Without
                    // V052 first, every tenant insert fails role_action_action_code_fkey.
                    if (!actionExists(conn, "payroll.fbp.read")) {
                        executeResource(conn, "db/migration/reference/V052__fbp_actions.sql");
                    }
                    if (!actionExists(conn, "payroll.reimbursement_claim.read")) {
                        executeResource(conn, "db/migration/reference/V097__reimbursement_claim_actions.sql");
                    }
                    // V135 grants W-35.2's payroll.employee_deduction.* codes, which only V100 inserts.
                    if (!actionExists(conn, "payroll.employee_deduction.read")) {
                        executeResource(conn, "db/migration/reference/V100__employee_deduction_actions.sql");
                    }
                    if (!actionExists(conn, "hrms.project.manage")) {
                        executeResource(conn, "db/migration/core/V085__hrms_project_actions.sql");
                        // V135 is the one current seed_system_roles: V100's grants plus hrms.project.*.
                        executeResource(conn, "db/migration/core/V135__hrms_project_seed_roles.sql");
                    }
                    if (!actionExists(conn, "hrms.overtime.request")) {
                        executeResource(conn, "db/migration/reference/V122__hrms_request_actions.sql");
                        executeResource(conn, "db/migration/core/V139__hrms_request_seed_roles.sql");
                    }
                    if (!functionExists(conn, "core", "list_tenants")) {
                        executeResource(conn, "db/migration/core/V082__platform_tenant.sql");
                    }
                    // W-65.1 review: V082 wrote the platform tenant's subscription status in lower case, which the
                    // SubscriptionStatus enum cannot read. V138 corrects it and adds the CHECK.
                    if (!constraintExists(conn, "core", "subscription", "ck_subscription_status")) {
                        executeResource(conn, "db/migration/core/V138__subscription_status_check.sql");
                    }
                    if (!actionExists(conn, "core.tenant.impersonate")) {
                        executeResource(conn, "db/migration/reference/V083__action_impersonate.sql");
                    }
                    if (!tableExists(conn, "core", "impersonation_session")) {
                        executeResource(conn, "db/migration/core/V084__impersonation_session.sql");
                    }
                    // W-24.1: tenant setup step
                    executeResource(conn, "db/migration/core/V035__tenant_setup_step.sql");
                    // W-17: holiday calendar
                    executeResource(conn, "db/migration/core/V036__holiday_calendar.sql");
                    // W-24.2: user and employee invitations
                    executeResource(conn, "db/migration/core/V117__user_invitation.sql");
                    executeResource(conn, "db/migration/core/V118__employee_invitation.sql");
                    // W-73.3: role_ids on employee_invitation; the token lookup function returns it
                    executeResource(conn, "db/migration/core/V163__employee_invitation_roles.sql");
                    // D-42: list_tenants() and get_tenant_overview() report the administrator
                    // invitation, reading the V117 tables; TenantQueryService maps the new columns.
                    executeResource(conn, "db/migration/core/V159__list_tenants_admin_invitation.sql");
                    // W-73.2: the platform dashboard's waiting administrator invitations, read across RLS.
                    executeResource(conn, "db/migration/core/V168__list_waiting_admin_invitations.sql");
                    // W-73.4: cross-tenant Keycloak-user states for Disable, and resolve_impersonation
                    // granting nothing for a disabled target (replaces V084's function)
                    executeResource(conn, "db/migration/core/V164__user_account_disable.sql");
                    // W-18.1: loss-of-pay policy
                    executeResource(conn, "db/migration/core/V116__lop_policy.sql");
                    if (!tableExists(conn, "core", "document")) {
                        executeResource(conn, "db/migration/core/V037__document.sql");
                        executeResource(conn, "db/migration/core/V166__document_label.sql");
                    }
                    // W-73.1: logo and tagline on core.tenant, and the TENANT_LOGO document kind.
                    if (!columnExists(conn, "core", "tenant", "logo_document_id")) {
                        executeResource(conn, "db/migration/core/V160__tenant_branding.sql");
                    }
                    if (!tableExists(conn, "core", "leave_type")) {
                        executeResource(conn, "db/migration/core/V126__leave_type.sql");
                        executeResource(conn, "db/migration/core/V127__leave_policy.sql");
                        executeResource(conn, "db/migration/core/V128__leave_policy_eligibility.sql");
                        executeResource(conn, "db/migration/core/V129__leave_allocation.sql");
                        executeResource(conn, "db/migration/core/V130__leave_request.sql");
                        executeResource(conn, "db/migration/core/V131__leave_request_document.sql");
                    }
                    if (!tableExists(conn, "core", "pay_input")) {
                        executeResource(conn, "db/migration/core/V031__pay_input.sql");
                    }
                    if (!tableExists(conn, "core", "leave_consumption")) {
                        executeResource(conn, "db/migration/core/V132__leave_consumption.sql");
                    }
                    if (!tableExists(conn, "core", "leave_monthly_lop")) {
                        executeResource(conn, "db/migration/core/V133__leave_monthly_lop.sql");
                    }
                    if (!tableExists(conn, "core", "leave_import_log")) {
                        executeResource(conn, "db/migration/core/V134__leave_import_log.sql");
                    }
                    // D-33: the platform tenant's roles keep platform actions only. Last, as in Flyway: it
                    // rewrites core.seed_system_roles (V148's body) and refuses any other platform grant.
                    if (!functionExists(conn, "core", "restrict_platform_tenant_grant")) {
                        executeResource(conn, "db/migration/core/V158__platform_tenant_role_scope.sql");
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

    /** Inserts an employee in a tenant, as the schema owner. */
    public static UUID insertEmployee(UUID tenantId, String employeeNumber, String firstName) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (tenant_id, employee_number, first_name, date_of_joining, status, created_by, updated_by)
                        VALUES (?, ?, ?, '2026-01-01', 'ACTIVE', 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, employeeNumber);
            ps.setString(3, firstName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
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

    /**
     * A document row written directly as the owner, with no blob — for W-73.1's logo reference. {@code kind}
     * and {@code contentType} are taken as given so a test can hand the profile endpoint the wrong kind.
     */
    public static UUID insertDocumentRow(UUID tenantId, String kind, String contentType, long sizeBytes)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.document (id, tenant_id, employee_id, kind, file_name, content_type,
                                                   size_bytes, blob_container, blob_path, checksum_sha256)
                        VALUES (?, ?, NULL, ?, 'logo.png', ?, ?, 'documents', ?, ?)
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, kind);
            ps.setString(4, contentType);
            ps.setLong(5, sizeBytes);
            ps.setString(6, tenantId + "/tenant/" + kind + "/" + id);
            ps.setString(7, "0".repeat(64));
            ps.executeUpdate();
        }
        return id;
    }

    private static boolean columnExists(Connection conn, String schema, String table, String column)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
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

    private static boolean actionExists(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM reference.action WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean constraintExists(Connection conn, String schema, String table, String constraint)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT 1 FROM pg_constraint c
                  JOIN pg_class t ON t.oid = c.conrelid
                  JOIN pg_namespace n ON n.oid = t.relnamespace
                 WHERE n.nspname = ? AND t.relname = ? AND c.conname = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, constraint);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean functionExists(Connection conn, String schema, String function) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = ? AND p.proname = ?")) {
            ps.setString(1, schema);
            ps.setString(2, function);
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
