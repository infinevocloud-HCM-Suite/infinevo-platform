package com.infinevo.payroll.payrun;

import com.infinevo.payroll.PayrollTestSchema;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Schema and fixtures for the W-29.1 integration tests: {@link PayrollTestSchema}'s tables plus the
 * bank section (W-13.2), the pay schedule (W-28), the two pay run tables and the lines (W-29.2). Rows are written as
 * {@code migration_user}; the tests read through the services, or as {@code app_user} for RLS.
 */
public final class PayRunTestSchema {

    public static final UUID TENANT_A = PayrollTestSchema.TENANT_A;
    public static final UUID TENANT_B = PayrollTestSchema.TENANT_B;

    private PayRunTestSchema() {}

    public static void apply() throws Exception {
        PayrollTestSchema.apply();
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            // W-18.1's loss-of-pay policy (W-29.3): V116 needs the tenant locale columns and the
            // subscription table its provision_tenant function writes.
            if (!PayrollTestSchema.columnExists(conn, "core", "tenant", "country_code")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V033__tenant_locale_columns.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "core", "subscription")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V034__subscription.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "core", "lop_policy")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V116__lop_policy.sql");
            }
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
            if (!PayrollTestSchema.tableExists(conn, "payroll", "employee_payrun_line")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V057__employee_payrun_line.sql");
            }
            if (!PayrollTestSchema.columnExists(conn, "payroll", "employee_payrun", "lop_days")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V058__employee_payrun_days.sql");
            }
            // W-29.4: the job a compute creates, and the attempt and progress columns.
            if (!PayrollTestSchema.tableExists(conn, "core", "job_status")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V006__job_status_and_shedlock.sql");
            }
            if (!PayrollTestSchema.columnExists(conn, "payroll", "payrun", "compute_attempt")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V059__payrun_job_progress.sql");
            }
            // W-30.2: the off-cycle run type, the notes column and the narrowed one-per-period index.
            if (!PayrollTestSchema.columnExists(conn, "payroll", "payrun", "notes")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V061__payrun_off_cycle.sql");
            }
            // W-18.2: the policy stamp on every computed row.
            if (!PayrollTestSchema.columnExists(conn, "payroll", "employee_payrun", "lop_policy_id")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V125__employee_payrun_policy_stamp.sql");
            }
            // W-36.2: approve and pay columns on payrun.
            if (!PayrollTestSchema.columnExists(conn, "payroll", "payrun", "approved_at")) {
                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V103__payrun_approve_pay.sql");
            }
            // W-20.1: notification templates and notifications for payslip link dispatch.
            if (!PayrollTestSchema.tableExists(conn, "core", "notification_template")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V038__notification_template.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "core", "notification")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V039__notification.sql");
            }
            try (Statement st = conn.createStatement()) {
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA core TO app_user");
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payroll TO app_user");
            }
        }
        PayrollTestSchema.seedTenants();
    }

    /**
     * Pay runs, their rows, pay inputs, salaries, bank sections, employees and schedules of both
     * tenants; then both tenants back on the default policy — ACTUAL_DAYS, weekends and holidays
     * payable — so payable days equal calendar days and a full month has no loss of pay.
     */
    public static void clean() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            if (PayrollTestSchema.tableExists(conn, "core", "notification")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("DELETE FROM core.notification");
                }
            }
        }
        PayrollTestSchema.cleanTables();
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            try (PreparedStatement ps =
                    conn.prepareStatement("DELETE FROM core.job_status WHERE tenant_id IN (?, ?)")) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, TENANT_B);
                ps.executeUpdate();
            }
        }
        setPolicy(TENANT_A, "ACTUAL_DAYS", true, true, "HALF_UP_2");
        setPolicy(TENANT_B, "ACTUAL_DAYS", true, true, "HALF_UP_2");
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("DELETE FROM payroll.pay_schedule WHERE tenant_id IN (?, ?)")) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, TENANT_B);
            ps.executeUpdate();
        }
    }

    /** Replaces the tenant's loss-of-pay policy with one version in force since 1900 (W-18.1). */
    static void setPolicy(
            UUID tenantId, String basis, boolean weekendsPayable, boolean holidaysPayable, String rounding)
            throws SQLException {
        setPolicy(tenantId, basis, null, weekendsPayable, holidaysPayable, rounding);
    }

    /** As above with {@code configured_days_per_month} — ORG_DAYS with a fixed count (W-18.2). */
    static void setPolicy(
            UUID tenantId,
            String basis,
            BigDecimal configuredDaysPerMonth,
            boolean weekendsPayable,
            boolean holidaysPayable,
            String rounding)
            throws SQLException {
        deletePolicy(tenantId);
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.lop_policy (tenant_id, working_day_basis, configured_days_per_month, "
                                + "weekends_payable, holidays_payable, lop_rounding, effective_from) "
                                + "VALUES (?, ?, ?, ?, ?, ?, DATE '1900-01-01')")) {
            ps.setObject(1, tenantId);
            ps.setString(2, basis);
            ps.setBigDecimal(3, configuredDaysPerMonth);
            ps.setBoolean(4, weekendsPayable);
            ps.setBoolean(5, holidaysPayable);
            ps.setString(6, rounding);
            ps.executeUpdate();
        }
    }

    /** The tenant's policy version in force for July 2026 — what a July row's stamp must name. */
    static UUID policyId(UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT id FROM core.lop_policy WHERE tenant_id = ?"
                        + " AND effective_from <= DATE '2026-07-31' ORDER BY effective_from DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (UUID) rs.getObject(1) : null;
            }
        }
    }

    /** One included row's figure state and its five stamp columns, as the database holds them (W-18.2). */
    record StampRow(
            UUID employeeId,
            String computationError,
            UUID lopPolicyId,
            String workingDayBasis,
            BigDecimal payDivisor,
            BigDecimal payableDays,
            String lopRounding) {

        boolean fullyStamped() {
            return lopPolicyId != null
                    && workingDayBasis != null
                    && payDivisor != null
                    && payableDays != null
                    && lopRounding != null;
        }

        boolean unstamped() {
            return lopPolicyId == null
                    && workingDayBasis == null
                    && payDivisor == null
                    && payableDays == null
                    && lopRounding == null;
        }
    }

    static List<StampRow> stamps(UUID tenantId, UUID payrunId) throws SQLException {
        List<StampRow> result = new ArrayList<>();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT employee_id, computation_error, lop_policy_id,"
                        + " working_day_basis, pay_divisor, payable_days, lop_rounding FROM payroll.employee_payrun"
                        + " WHERE tenant_id = ? AND payrun_id = ? AND inclusion_status = 'INCLUDED'")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, payrunId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new StampRow(
                            (UUID) rs.getObject(1),
                            rs.getString(2),
                            (UUID) rs.getObject(3),
                            rs.getString(4),
                            rs.getBigDecimal(5),
                            rs.getBigDecimal(6),
                            rs.getString(7)));
                }
            }
        }
        return result;
    }

    static void deletePolicy(UUID tenantId) throws SQLException {
        execute("DELETE FROM core.lop_policy WHERE tenant_id = ?", tenantId);
    }

    public static UUID insertEmployee(
            UUID tenantId, String number, LocalDate joined, String status, LocalDate terminated) throws SQLException {
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
    public static UUID insertSalary(UUID tenantId, UUID employeeId, LocalDate effectiveFrom) throws SQLException {
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

    public static void insertBank(UUID tenantId, UUID employeeId) throws SQLException {
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

    /** A catalogue earning; returns its id. */
    public static UUID insertEarningComponent(
            UUID tenantId, String code, String name, boolean variable, boolean taxable, boolean fbp)
            throws SQLException {
        return insertReturningId(
                "INSERT INTO payroll.earning (tenant_id, code, name, earning_type, is_variable, is_taxable, "
                        + "is_fbp_component, is_included_in_ctc) VALUES (?, ?, ?, 'FIXED', ?, ?, ?, true) RETURNING id",
                tenantId,
                code,
                name,
                variable,
                taxable,
                fbp);
    }

    public static UUID insertBenefitComponent(UUID tenantId, String code, String name) throws SQLException {
        return insertReturningId(
                "INSERT INTO payroll.benefit (tenant_id, code, name, is_included_in_ctc) VALUES (?, ?, ?, true) RETURNING id",
                tenantId,
                code,
                name);
    }

    public static UUID insertReimbursementComponent(UUID tenantId, String code, String name) throws SQLException {
        return insertReturningId(
                "INSERT INTO payroll.reimbursement (tenant_id, code, name, reimbursement_type, is_included_in_ctc) "
                        + "VALUES (?, ?, ?, 'ALLOWANCE', true) RETURNING id",
                tenantId,
                code,
                name);
    }

    /** One structure line on a salary version: {@code table} is employee_earning, _benefit or _reimbursement. */
    public static void insertStructureLine(
            String table, UUID tenantId, UUID ctcId, UUID componentId, String monthly, String frequency)
            throws SQLException {
        boolean earning = "employee_earning".equals(table);
        String sql = "INSERT INTO payroll." + table
                + " (tenant_id, ctc_structure_id, component_id, value, monthly_amount, annual_amount"
                + (earning ? ", earning_frequency" : "")
                + ") VALUES (?, ?, ?, ?, ?, ?" + (earning ? ", ?" : "") + ")";
        BigDecimal m = new BigDecimal(monthly);
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ctcId);
            ps.setObject(3, componentId);
            ps.setBigDecimal(4, m);
            ps.setBigDecimal(5, m);
            ps.setBigDecimal(6, m.multiply(BigDecimal.valueOf(12)));
            if (earning) {
                ps.setString(7, frequency);
            }
            ps.executeUpdate();
        }
    }

    public static void insertFbpDeclaration(UUID tenantId, UUID ctcId, UUID employeeId, UUID earningId, String monthly)
            throws SQLException {
        BigDecimal m = new BigDecimal(monthly);
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.employee_fbp_component (tenant_id, ctc_structure_id, employee_id, "
                                + "earning_id, annual_amount, monthly_amount, declared_at, declared_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, now(), 'EMPLOYEE')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ctcId);
            ps.setObject(3, employeeId);
            ps.setObject(4, earningId);
            ps.setBigDecimal(5, m.multiply(BigDecimal.valueOf(12)));
            ps.setBigDecimal(6, m);
            ps.executeUpdate();
        }
    }

    /** The W-29.2 §8 catalogue for one tenant. */
    public record Catalogue(UUID basic, UUID hra, UUID special, UUID meal, UUID bonus, UUID employerPf, UUID fuel) {}

    public static Catalogue insertWorkedExampleCatalogue(UUID tenantId) throws SQLException {
        return new Catalogue(
                insertEarningComponent(tenantId, "BASIC", "Basic", false, true, false),
                insertEarningComponent(tenantId, "HRA", "House rent allowance", false, true, false),
                insertEarningComponent(tenantId, "SPECIAL", "Special allowance", false, true, false),
                insertEarningComponent(tenantId, "MEAL", "Meal card", false, true, true),
                insertEarningComponent(tenantId, "BONUS", "Annual bonus", true, true, false),
                insertBenefitComponent(tenantId, "EMPLOYER_PF", "Employer PF"),
                insertReimbursementComponent(tenantId, "FUEL", "Fuel reimbursement"));
    }

    /**
     * An employee paid the §8 worked example: active since 2023-04, a bank section, and a salary
     * version from 2025-01 whose July lines net 47,000.00. Returns the employee id.
     */
    public static UUID insertWorkedExampleEmployee(UUID tenantId, String number, Catalogue c) throws SQLException {
        UUID employee = insertEmployee(tenantId, number, LocalDate.of(2023, 4, 1), "ACTIVE", null);
        insertBank(tenantId, employee);
        UUID ctc = insertSalary(tenantId, employee, LocalDate.of(2025, 1, 1));
        insertStructureLine("employee_earning", tenantId, ctc, c.basic(), "25000.0000", null);
        insertStructureLine("employee_earning", tenantId, ctc, c.hra(), "10000.0000", null);
        insertStructureLine("employee_earning", tenantId, ctc, c.special(), "7500.0000", null);
        insertStructureLine("employee_earning", tenantId, ctc, c.meal(), "2500.0000", null);
        insertFbpDeclaration(tenantId, ctc, employee, c.meal(), "1500.0000");
        insertStructureLine("employee_earning", tenantId, ctc, c.bonus(), "5000.0000", "YEARLY");
        insertStructureLine("employee_benefit", tenantId, ctc, c.employerPf(), "1800.0000", null);
        insertStructureLine("employee_reimbursement", tenantId, ctc, c.fuel(), "2000.0000", null);
        return employee;
    }

    /** W-29.3 §8: Basic, HRA, Special allowance and the employer PF benefit are pro-rata; the meal card is not. */
    static void markWorkedExampleProRata(UUID tenantId) throws SQLException {
        execute(
                "UPDATE payroll.earning SET is_pro_rata = true WHERE tenant_id = ? AND code IN ('BASIC', 'HRA', 'SPECIAL')",
                tenantId);
        execute("UPDATE payroll.benefit SET is_pro_rata = true WHERE tenant_id = ? AND code = 'EMPLOYER_PF'", tenantId);
    }

    static long countLines(UUID tenantId, UUID payrunId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM payroll.employee_payrun_line WHERE tenant_id = ? AND payrun_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, payrunId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** A job row as the database holds it (W-29.4). */
    record JobRow(String status, int progress, Instant updatedAt) {}

    static JobRow job(String jobId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT status, progress_percentage, updated_at FROM core.job_status WHERE job_id = ?")) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new JobRow(
                        rs.getString(1),
                        rs.getInt(2),
                        rs.getObject(3, OffsetDateTime.class).toInstant());
            }
        }
    }

    /** The job last moved {@code minutes} ago: what a dead worker leaves behind (W-29.4 §3 stale rule). */
    static void backdateJob(String jobId, int minutes) throws SQLException {
        execute(
                "UPDATE core.job_status SET updated_at = now() - make_interval(mins => ?) WHERE job_id = ?",
                minutes,
                jobId);
    }

    /** The run's own counter last moved {@code minutes} ago — the other half of the stale rule. */
    static void backdateRun(UUID payrunId, int minutes) throws SQLException {
        execute(
                "UPDATE payroll.payrun SET updated_at = now() - make_interval(mins => ?) WHERE id = ?",
                minutes,
                payrunId);
    }

    /** Included rows of the run stamped with {@code attempt}. */
    static int rowsAtAttempt(UUID tenantId, UUID payrunId, int attempt) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll.employee_payrun"
                        + " WHERE tenant_id = ? AND payrun_id = ? AND inclusion_status = 'INCLUDED'"
                        + " AND computed_attempt = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, payrunId);
            ps.setInt(3, attempt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Each included row's {@code computed_at}, by row id — to prove a row was not written again. */
    static Map<UUID, Instant> computedAtByRow(UUID tenantId, UUID payrunId) throws SQLException {
        Map<UUID, Instant> result = new HashMap<>();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT id, computed_at FROM payroll.employee_payrun"
                        + " WHERE tenant_id = ? AND payrun_id = ? AND inclusion_status = 'INCLUDED'")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, payrunId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OffsetDateTime at = rs.getObject(2, OffsetDateTime.class);
                    result.put((UUID) rs.getObject(1), at == null ? null : at.toInstant());
                }
            }
        }
        return result;
    }

    public static void execute(String sql, Object... params) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    private static UUID insertReturningId(String sql, Object... params) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
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

    /** W-30.2: lock rows an off-cycle run wrote for itself — {@code run_ref} set. */
    static int countRunLocks(UUID tenantId, UUID runRef) throws SQLException {
        return countWhere(
                "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND run_ref = ?",
                tenantId,
                runRef);
    }

    /** W-30.2: ledger rows tagged with {@code runRef}. */
    static int countTaggedInputs(UUID tenantId, UUID runRef) throws SQLException {
        return countWhere("SELECT count(*) FROM core.pay_input WHERE tenant_id = ? AND run_ref = ?", tenantId, runRef);
    }

    private static int countWhere(String sql, Object... params) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
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

    public static UUID getEmployeePayRunId(UUID tenantId, UUID payrunId, UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM payroll.employee_payrun WHERE tenant_id = ? AND payrun_id = ? AND employee_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, payrunId);
            ps.setObject(3, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject(1);
                }
                throw new IllegalStateException("No employee_payrun row found");
            }
        }
    }
}
