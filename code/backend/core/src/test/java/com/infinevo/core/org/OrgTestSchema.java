package com.infinevo.core.org;

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

/**
 * Schema, seed data and owner-level reads shared by the org-master integration tests (W-14.1).
 *
 * <p>The shipped migrations are applied as written — {@code V001__tenant.sql},
 * {@code V010__employee.sql}, {@code V011__department.sql}, {@code V012__designation.sql},
 * {@code V013__work_location.sql} and {@code V014__employee_org_columns.sql} — so the tables under
 * test are the migrated ones and not copies that have drifted from them.
 *
 * <p><strong>Every application is guarded.</strong> The Postgres container is shared by every
 * integration test in the module and the classes run in an order nobody controls, so whichever runs
 * first creates the tables and the next one to run the same script unguarded dies on
 * {@code relation "tenant" already exists}. {@code EmployeeTestSchema} and {@code JobStatusTenantIT}
 * guard for exactly this reason.
 *
 * <p>Two connections, and the difference is the whole point of these tests.
 * {@link #migrationConnection()} is the schema owner: it bypasses row-level security and is how a row
 * gets planted and inspected. {@link #appConnection()} is {@code app_user}, the role the application
 * actually connects as, which holds no {@code BYPASSRLS} — anything read through it has passed the
 * {@code tenant_isolation} policy.
 */
final class OrgTestSchema {

    /** Acme Manufacturing — {@code infra/docker/seed/01-tenants.sql}. */
    static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Globex Corporation — {@code infra/docker/seed/01-tenants.sql}. */
    static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private OrgTestSchema() {}

    static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    /** Applies the shipped migrations this feature needs, each only if it has not landed yet. */
    static void apply() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "employee")) {
                executeResource(conn, "db/migration/core/V010__employee.sql");
            }
            if (!tableExists(conn, "department")) {
                executeResource(conn, "db/migration/core/V011__department.sql");
            }
            if (!tableExists(conn, "designation")) {
                executeResource(conn, "db/migration/core/V012__designation.sql");
            }
            if (!tableExists(conn, "work_location")) {
                executeResource(conn, "db/migration/core/V013__work_location.sql");
            }
            // V014 creates no table, so the guard is on the column it adds. Without this the second
            // test class to run fails on "column department_id of relation employee already exists".
            if (!columnExists(conn, "employee", "department_id")) {
                executeResource(conn, "db/migration/core/V014__employee_org_columns.sql");
            }
        }
    }

    /** Seeds the two dev tenants. Idempotent. */
    static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Acme Manufacturing");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Globex Corporation");
            ps.executeUpdate();
        }
    }

    /**
     * Empties the four tables, employees first.
     *
     * <p>The order is the foreign keys': {@code core.employee} points at all three masters since
     * {@code V014__employee_org_columns.sql}, so clearing a master before the employees holding it
     * fails with a constraint error that says nothing about the test that ran.
     */
    static void clearAll() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM core.employee");
            stmt.execute("DELETE FROM core.department");
            stmt.execute("DELETE FROM core.designation");
            stmt.execute("DELETE FROM core.work_location");
        }
    }

    /** Inserts a department directly, as the schema owner, so it exists without the service creating it. */
    static UUID seedDepartment(UUID tenantId, String code, String name, boolean active) throws SQLException {
        return insertMaster("department", tenantId, code, name, active);
    }

    /** Inserts a designation directly, as the schema owner. */
    static UUID seedDesignation(UUID tenantId, String code, String name, boolean active) throws SQLException {
        return insertMaster("designation", tenantId, code, name, active);
    }

    /**
     * Inserts a work location directly, as the schema owner, active, with the filing flag as asked.
     *
     * <p>Note the fourth argument is {@code filingAddress} here and {@code active} on the other two
     * seeders — same shape, different meaning, which is a trap. {@link #seedWorkLocation(UUID,
     * String, String, boolean, boolean)} takes both explicitly; prefer it when either matters.
     */
    static UUID seedWorkLocation(UUID tenantId, String code, String name, boolean filingAddress) throws SQLException {
        return seedWorkLocation(tenantId, code, name, filingAddress, true);
    }

    /**
     * The same, with {@code active} stated too — so a test can seed an <em>inactive</em> work
     * location. Until this existed none could, which meant the service's "an inactive master cannot
     * be newly assigned" rule was proved for department only.
     */
    static UUID seedWorkLocation(UUID tenantId, String code, String name, boolean filingAddress, boolean active)
            throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.work_location
                            (tenant_id, code, name, is_filing_address, is_active, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.setBoolean(4, filingAddress);
            ps.setBoolean(5, active);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** Inserts an employee directly, as the schema owner, optionally already holding a department. */
    static UUID seedEmployee(UUID tenantId, String employeeNumber, String firstName, UUID departmentId)
            throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (tenant_id, employee_number, first_name, date_of_joining, status,
                             department_id, created_by, updated_by)
                        VALUES (?, ?, ?, ?, 'ACTIVE', ?, 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, employeeNumber);
            ps.setString(3, firstName);
            ps.setObject(4, LocalDate.of(2026, 4, 1));
            ps.setObject(5, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** Counts rows of one table for one tenant, bypassing row-level security — the control for the assertions. */
    static int countFor(String table, UUID tenantId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core." + table + " WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** One column of one row of one table, read as the schema owner. */
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

    /**
     * Whether {@code app_user} can see one specific row with {@code tenantId} bound — the hard read,
     * with no Java in the way.
     *
     * <p>It has to be this and not a call through the service. A service that filtered by tenant in
     * Java would answer "not found" whether the policy works or not, so a test that only asked the
     * service could not tell an enforced policy from a {@code WHERE} clause.
     */
    static boolean visibleToAppUser(String table, UUID tenantId, UUID id) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core." + table + " WHERE id = ?")) {
                    ps.setObject(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }

    /** How many rows of one table {@code app_user} can see at all with {@code tenantId} bound. */
    static int visibleRowCount(String table, UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core." + table)) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    private static UUID insertMaster(String table, UUID tenantId, String code, String name, boolean active)
            throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO core." + table
                        + " (tenant_id, code, name, is_active, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, 'test', 'test') RETURNING id")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.setBoolean(4, active);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
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

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT 1 FROM information_schema.columns
                 WHERE table_schema = 'core' AND table_name = ? AND column_name = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = OrgTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
