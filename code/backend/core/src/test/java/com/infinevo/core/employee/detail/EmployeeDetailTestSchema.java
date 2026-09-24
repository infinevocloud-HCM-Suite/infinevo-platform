package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.EmployeeTestSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * Schema, seed data and owner-level reads shared by the employee detail integration tests (W-13.2).
 *
 * <p>The shipped migrations are applied as written — {@code V015__employee_personal.sql} through
 * {@code V019__employee_bank.sql} — so the tables under test are the migrated ones and not copies
 * that have drifted from them. Each is guarded on its own table existing, the idempotent way
 * {@code EmployeeTestSchema} applies {@code V010}-{@code V014}, because the Postgres container is
 * shared by every integration test in this module.
 *
 * <p>A sibling of {@code EmployeeTestSchema} rather than a replacement. The tenants, the two
 * connections and the seeded employee come from it — a second copy of those would be two answers to
 * "which tenant is Acme" and two employees for the tests to disagree about.
 *
 * <p>Two connections, and the difference is the whole point of these tests.
 * {@link EmployeeTestSchema#migrationConnection()} is the schema owner: it bypasses row-level
 * security and is how a row gets planted and inspected. {@link EmployeeTestSchema#appConnection()}
 * is {@code app_user}, the role the application actually connects as, which holds no
 * {@code BYPASSRLS} — anything read through it has passed the {@code tenant_isolation} policy the
 * five migrations carry.
 */
public final class EmployeeDetailTestSchema {

    /** The five tables, in migration order. Every helper below walks this list rather than naming one. */
    public static final List<String> SECTION_TABLES = List.of(
            "employee_personal", "employee_contact", "employee_identification", "employee_employment", "employee_bank");

    private EmployeeDetailTestSchema() {}

    /**
     * Applies the shipped migrations this feature needs, on top of the ones
     * {@code EmployeeTestSchema} applies. Idempotent — other suites share the container.
     *
     * <p>{@code V008__audit_log.sql} is applied too. It is not a W-13.2 table, but the five entities
     * carry {@code @Audited} as of this ticket, so a context that registers the audit listener writes
     * to {@code core.audit_log} on every section write — and without the table that write fails with
     * "relation does not exist" from inside a post-commit hook, which reads like an outage rather
     * than a missing migration.
     */
    public static void apply() throws Exception {
        EmployeeTestSchema.apply();
        try (Connection conn = EmployeeTestSchema.migrationConnection()) {
            if (!EmployeeTestSchema.tableExists(conn, "audit_log")) {
                EmployeeTestSchema.executeResource(conn, "db/migration/core/V008__audit_log.sql");
            }
            if (!EmployeeTestSchema.tableExists(conn, "employee_personal")) {
                EmployeeTestSchema.executeResource(conn, "db/migration/core/V015__employee_personal.sql");
            }
            if (!EmployeeTestSchema.tableExists(conn, "employee_contact")) {
                EmployeeTestSchema.executeResource(conn, "db/migration/core/V016__employee_contact.sql");
            }
            if (!EmployeeTestSchema.tableExists(conn, "employee_identification")) {
                EmployeeTestSchema.executeResource(conn, "db/migration/core/V017__employee_identification.sql");
            }
            if (!EmployeeTestSchema.tableExists(conn, "employee_employment")) {
                EmployeeTestSchema.executeResource(conn, "db/migration/core/V018__employee_employment.sql");
            }
            if (!EmployeeTestSchema.tableExists(conn, "employee_bank")) {
                EmployeeTestSchema.executeResource(conn, "db/migration/core/V019__employee_bank.sql");
            }
        }
    }

    /**
     * Removes every detail row, as the schema owner. For {@code @BeforeEach} and {@code @AfterAll}.
     *
     * <p>Detail rows first, then employees: all five tables hold a foreign key to
     * {@code core.employee}, so clearing employees while a section survives fails with a constraint
     * error that says nothing about either feature.
     */
    public static void clearAll() throws SQLException {
        clearSections();
        EmployeeTestSchema.clearEmployees();
    }

    /** Removes every detail row and leaves the employees alone. */
    public static void clearSections() throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                Statement stmt = conn.createStatement()) {
            for (String table : SECTION_TABLES) {
                stmt.execute("DELETE FROM core." + table);
            }
        }
    }

    /** Removes the audit rows these tests wrote, as the schema owner, so a count can start from zero. */
    public static void clearAudit() throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM core.audit_log WHERE tenant_id IN ('" + EmployeeTestSchema.TENANT_A + "','"
                    + EmployeeTestSchema.TENANT_B + "')");
        }
    }

    /**
     * Plants one row in each of the five tables for an employee, as the schema owner, so row-level
     * security can be tested against rows the application under test never created.
     *
     * <p>Only the columns a test asserts on are set; everything else takes its database default. The
     * bank row names {@code payment_mode} because the column is {@code NOT NULL}.
     */
    public static void seedAllSections(UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection()) {
            insert(conn, "employee_personal", tenantId, employeeId, "nationality", "Indian");
            insert(conn, "employee_contact", tenantId, employeeId, "city", "Pune");
            insert(conn, "employee_identification", tenantId, employeeId, "pan_number", "ABCDE1234F");
            insert(conn, "employee_employment", tenantId, employeeId, "pay_grade", "G5");
            insert(conn, "employee_bank", tenantId, employeeId, "payment_mode", "BANK_TRANSFER");
        }
    }

    private static void insert(
            Connection conn, String table, UUID tenantId, UUID employeeId, String column, String value)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core." + table
                + " (tenant_id, employee_id, " + column + ", created_by, updated_by)"
                + " VALUES (?, ?, ?, 'test', 'test')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    /** Counts rows in one section table for one tenant, bypassing RLS — the control for the assertions. */
    public static int countSection(String table, UUID tenantId) throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core." + table + " WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Counts rows in one section table for one employee, bypassing RLS. Proves there is never a second. */
    public static int countSectionForEmployee(String table, UUID employeeId) throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core." + table + " WHERE employee_id = ?")) {
            ps.setObject(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** One column of one section row, read as the schema owner. */
    public static Object readColumn(String table, UUID employeeId, String column) throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT " + column + " FROM core." + table + " WHERE employee_id = ?")) {
            ps.setObject(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        }
    }

    /**
     * How many rows of one section table {@code app_user} can see for one employee, with
     * {@code tenantId} bound — the hard read spec section 7 asks for.
     *
     * <p>It has to be this and not a call through the service: a service-level "not found" looks
     * identical whether the policy works or not. Same method {@code EmployeeRlsIT} uses.
     */
    public static int visibleToAppUser(String table, UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = EmployeeTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core." + table + " WHERE employee_id = ?")) {
                    ps.setObject(1, employeeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getInt(1);
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }

    /** How many rows of one section table {@code app_user} can see at all with {@code tenantId} bound. */
    public static int visibleRowCount(String table, UUID tenantId) throws SQLException {
        try (Connection conn = EmployeeTestSchema.appConnection()) {
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

    /**
     * Tries one insert as {@code app_user} with {@code tenantId} bound, and rolls it back whatever
     * happens.
     *
     * <p>Used by {@code EmployeeDetailCascadeIT} to show what the database alone permits: the foreign
     * key on {@code employee_id} is checked as the table owner, which bypasses row-level security, so
     * a row in tenant A naming an employee in tenant B is accepted. The service is what refuses it.
     *
     * @return true when the insert was accepted
     */
    public static boolean tryInsertAsAppUser(String table, UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = EmployeeTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core." + table
                        + " (tenant_id, employee_id, created_by, updated_by) VALUES (?, ?, 'test', 'test')")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, employeeId);
                    ps.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                return false;
            } finally {
                conn.rollback();
            }
        }
    }

    private static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }
}
