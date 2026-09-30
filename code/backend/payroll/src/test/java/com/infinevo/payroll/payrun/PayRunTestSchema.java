package com.infinevo.payroll.payrun;

import com.infinevo.payroll.PayrollTestSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Schema and fixtures for the W-29.1 integration tests: {@link PayrollTestSchema}'s tables plus the
 * bank section (W-13.2), the pay schedule (W-28) and the two pay run tables. Rows are written as
 * {@code migration_user}; the tests read through the services, or as {@code app_user} for RLS.
 */
final class PayRunTestSchema {

    static final UUID TENANT_A = PayrollTestSchema.TENANT_A;
    static final UUID TENANT_B = PayrollTestSchema.TENANT_B;

    private PayRunTestSchema() {}

    static void apply() throws Exception {
        PayrollTestSchema.apply();
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            if (!PayrollTestSchema.tableExists(conn, "core", "employee_bank")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V019__employee_bank.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "payroll", "pay_schedule")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V054__pay_schedule.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "payroll", "payrun")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V055__payrun.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "payroll", "employee_payrun")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V056__employee_payrun.sql");
            }
            try (Statement st = conn.createStatement()) {
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA core TO app_user");
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payroll TO app_user");
            }
        }
        PayrollTestSchema.seedTenants();
    }

    /** Pay runs, their rows, pay inputs, salaries, bank sections, employees and schedules of both tenants. */
    static void clean() throws SQLException {
        PayrollTestSchema.cleanTables();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("DELETE FROM payroll.pay_schedule WHERE tenant_id IN (?, ?)")) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, TENANT_B);
            ps.executeUpdate();
        }
    }

    static UUID insertEmployee(UUID tenantId, String number, LocalDate joined, String status, LocalDate terminated)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (tenant_id, employee_number, first_name, last_name, date_of_joining, status,
                             termination_date, created_by, updated_by)
                        VALUES (?, ?, 'First', 'Last', ?, ?, ?, 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, number);
            ps.setObject(3, joined);
            ps.setString(4, status);
            ps.setObject(5, terminated);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** A salary version in force from {@code effectiveFrom}; returns its id. */
    static UUID insertSalary(UUID tenantId, UUID employeeId, LocalDate effectiveFrom) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.ctc_structure
                            (tenant_id, employee_id, effective_from, annual_ctc, monthly_ctc)
                        VALUES (?, ?, ?, 1200000.0000, 100000.0000)
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setObject(3, effectiveFrom);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    static void insertBank(UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee_bank (tenant_id, employee_id, payment_mode) VALUES (?, ?, 'BANK_TRANSFER')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.executeUpdate();
        }
    }

    /** An employee who will be INCLUDED: active since 2025, a salary from then, and a bank section. */
    static UUID insertPayableEmployee(UUID tenantId, String number) throws SQLException {
        UUID id = insertEmployee(tenantId, number, LocalDate.of(2025, 1, 1), "ACTIVE", null);
        insertSalary(tenantId, id, LocalDate.of(2025, 1, 1));
        insertBank(tenantId, id);
        return id;
    }

    static int countRuns(UUID tenantId, String period, boolean excludeCancelled) throws SQLException {
        String sql = "SELECT count(*) FROM payroll.payrun WHERE tenant_id = ? AND period = ?"
                + (excludeCancelled ? " AND status <> 'CANCELLED'" : "");
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, period);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static int countPeriodLocks(UUID tenantId, String period) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND period = ? AND run_ref IS NULL")) {
            ps.setObject(1, tenantId);
            ps.setString(2, period);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
