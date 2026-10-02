package com.infinevo.payroll.statutory.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.EmployeePayRunLineResponse;
import com.infinevo.payroll.payrun.EmployeePayRunLinesResponse;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLineContributor;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-31.4 §7 — the acceptance test: lock and compute a one-employee run with the §8 setup;
 * total_deductions = 2,000.0000, total_benefits = 1,950.0000, net_pay = 45,000.00 - 2,000.00 = 43,000.00
 * with no reimbursements; lines readable with source = STATUTORY; implements PayLineContributor count is 4.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunStatutoryIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private List<PayLineContributor> contributors;

    private UUID employeeId;
    private UUID workLocationId;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_A);

        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));

        workLocationId = UUID.randomUUID();
        seedWorkLocation(TENANT_A, workLocationId, "BLR-01", "Bengaluru HQ", "KA");

        employeeId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeId, "EMP-STAT-001", workLocationId);
        PayRunTestSchema.insertBank(TENANT_A, employeeId);
        seedStatutoryProfile(TENANT_A, employeeId, true, true, true, true);
        seedEpfSetting(TENANT_A);
        seedEsiSetting(TENANT_A);

        seedSalaryWithStatutory(TENANT_A, employeeId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Lock then compute: statutory lines produced, deductions=2,000, benefits=1,950, net=43,000")
    void computeStatutoryWorkedExample() throws SQLException {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());

        PayRunResponse computed = worker.computeNow(run.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(computed.computedAt()).isNotNull();
        assertThat(computed.failureReason()).isNull();

        assertThat(computed.totalGross()).isEqualByComparingTo("45000.0000");
        assertThat(computed.totalDeductions()).isEqualByComparingTo("2000.0000");
        assertThat(computed.totalNetPay()).isEqualByComparingTo("43000.00");

        BigDecimal rowBenefits = getRowTotalBenefits(run.id(), employeeId);
        assertThat(rowBenefits).isEqualByComparingTo("1950.0000");

        EmployeePayRunLinesResponse linesResponse = payRunService.lines(run.id(), employeeId);
        assertThat(linesResponse.computationError()).isNull();
        List<EmployeePayRunLineResponse> lines = linesResponse.lines();

        List<EmployeePayRunLineResponse> statutoryLines =
                lines.stream().filter(l -> l.source() == LineSource.STATUTORY).toList();

        assertThat(statutoryLines).isNotEmpty();

        assertThat(statutoryLines).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_EMPLOYEE");
            assertThat(line.lineKind()).isEqualTo(LineKind.DEDUCTION);
            assertThat(line.amount()).isEqualByComparingTo("1800.0000");
        });

        assertThat(statutoryLines).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("PROFESSIONAL_TAX");
            assertThat(line.lineKind()).isEqualTo(LineKind.DEDUCTION);
            assertThat(line.amount()).isEqualByComparingTo("200.0000");
        });

        assertThat(statutoryLines).noneMatch(line -> "ESI_EMPLOYEE".equals(line.componentCode()));
        assertThat(statutoryLines).noneMatch(line -> "ESI_EMPLOYER".equals(line.componentCode()));

        assertThat(statutoryLines).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPS_EMPLOYER");
            assertThat(line.lineKind()).isEqualTo(LineKind.BENEFIT);
            assertThat(line.amount()).isEqualByComparingTo("1250.0000");
        });

        assertThat(statutoryLines).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_EMPLOYER");
            assertThat(line.lineKind()).isEqualTo(LineKind.BENEFIT);
            assertThat(line.amount()).isEqualByComparingTo("550.0000");
        });

        assertThat(statutoryLines).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EDLI");
            assertThat(line.lineKind()).isEqualTo(LineKind.BENEFIT);
            assertThat(line.amount()).isEqualByComparingTo("75.0000");
        });

        assertThat(statutoryLines).anySatisfy(line -> {
            assertThat(line.componentCode()).isEqualTo("EPF_ADMIN");
            assertThat(line.lineKind()).isEqualTo(LineKind.BENEFIT);
            assertThat(line.amount()).isEqualByComparingTo("75.0000");
        });

        assertThat(contributors).hasSize(4);
    }

    private static void seedWorkLocation(UUID tenantId, UUID locationId, String code, String name, String stateCode)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.work_location (id, tenant_id, code, name, state_code, is_active) "
                                + "VALUES (?, ?, ?, ?, ?, true)")) {
            ps.setObject(1, locationId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, name);
            ps.setString(5, stateCode);
            ps.executeUpdate();
        }
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String number, UUID locationId)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (id, tenant_id, employee_number, first_name, last_name, gender, date_of_joining, status,
                             work_location_id, created_by, updated_by)
                        VALUES (?, ?, ?, 'Test', 'Employee', 'MALE', DATE '2023-04-01', 'ACTIVE', ?, 'test', 'test')
                        """)) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, number);
            ps.setObject(4, locationId);
            ps.executeUpdate();
        }
    }

    private static void seedStatutoryProfile(
            UUID tenantId, UUID employeeId, boolean pf, boolean eps, boolean esi, boolean pt) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_statutory_profile
                            (id, tenant_id, employee_id, is_eligible_for_pf, is_eligible_for_eps, is_eligible_for_esi,
                             is_eligible_for_pt, contributes_eps_on_higher_wages)
                        VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, false)
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setBoolean(3, pf);
            ps.setBoolean(4, eps);
            ps.setBoolean(5, esi);
            ps.setBoolean(6, pt);
            ps.executeUpdate();
        }
    }

    private static void seedEpfSetting(UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.epf_setting
                            (id, tenant_id, is_enabled, wage_ceiling, restrict_employee_to_ceiling, restrict_employer_to_ceiling,
                             employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate, eps_senior_age,
                             consider_earned_wage, prorate_restricted_wage)
                        VALUES (gen_random_uuid(), ?, true, 15000.0000, true, true, 12.0000, 12.0000, 8.3300, 0.5000, 0.5000, 58, true, false)
                        """)) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }

    private static void seedEsiSetting(UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.esi_setting
                            (id, tenant_id, is_enabled, wage_ceiling, employee_rate, employer_rate)
                        VALUES (gen_random_uuid(), ?, true, 21000.0000, 0.7500, 3.2500)
                        """)) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }

    private static void seedSalaryWithStatutory(UUID tenantId, UUID employeeId) throws SQLException {
        UUID basic = PayRunTestSchema.insertEarningComponent(tenantId, "BASIC", "Basic", false, true, false);
        UUID hra = PayRunTestSchema.insertEarningComponent(tenantId, "HRA", "House Rent Allowance", false, true, false);
        UUID special =
                PayRunTestSchema.insertEarningComponent(tenantId, "SPECIAL", "Special Allowance", false, true, false);
        UUID meal = PayRunTestSchema.insertEarningComponent(tenantId, "MEAL", "Meal Card", false, true, false);

        UUID ctc = PayRunTestSchema.insertSalary(tenantId, employeeId, LocalDate.of(2025, 1, 1));
        PayRunTestSchema.insertStructureLine("employee_earning", tenantId, ctc, basic, "25000.0000", null);
        PayRunTestSchema.insertStructureLine("employee_earning", tenantId, ctc, hra, "10000.0000", null);
        PayRunTestSchema.insertStructureLine("employee_earning", tenantId, ctc, special, "7500.0000", null);
        PayRunTestSchema.insertStructureLine("employee_earning", tenantId, ctc, meal, "2500.0000", null);

        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.ctc_epf_component
                            (id, tenant_id, ctc_structure_id, component_code, share, wage_base, rate, monthly_amount, annual_amount, is_included_in_ctc)
                        VALUES
                            (gen_random_uuid(), ?, ?, 'EPF_EMPLOYEE', 'EMPLOYEE', 15000.0000, 12.0000, 1800.0000, 21600.0000, false),
                            (gen_random_uuid(), ?, ?, 'EPS_EMPLOYER', 'EMPLOYER', 15000.0000, 8.3300, 1249.5000, 14994.0000, true),
                            (gen_random_uuid(), ?, ?, 'EPF_EMPLOYER', 'EMPLOYER', 15000.0000, 3.6700, 550.5000, 6606.0000, true),
                            (gen_random_uuid(), ?, ?, 'EDLI', 'EMPLOYER', 15000.0000, 0.5000, 75.0000, 900.0000, true),
                            (gen_random_uuid(), ?, ?, 'EPF_ADMIN', 'EMPLOYER', 15000.0000, 0.5000, 75.0000, 900.0000, true)
                        """)) {
            for (int i = 0; i < 5; i++) {
                ps.setObject(1 + i * 2, tenantId);
                ps.setObject(2 + i * 2, ctc);
            }
            ps.executeUpdate();
        }
    }

    private static BigDecimal getRowTotalBenefits(UUID payrunId, UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT total_benefits FROM payroll.employee_payrun WHERE payrun_id = ? AND employee_id = ?")) {
            ps.setObject(1, payrunId);
            ps.setObject(2, employeeId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
                throw new IllegalStateException("No employee_payrun row found");
            }
        }
    }
}
