package com.infinevo.payroll.statutory.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.EmployeePayRunLineResponse;
import com.infinevo.payroll.payrun.EmployeePayRunLinesResponse;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
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
 * W-31.4 §7 — a February run computed for a Tamil Nadu employee produces no PT line;
 * a March run does — the period decides, not the clock.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunStatutoryPeriodIT extends AbstractIntegrationTest {

    private static final YearMonth FEB = YearMonth.of(2026, 2);
    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

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
        seedWorkLocation(TENANT_A, workLocationId, "CHN-01", "Chennai Branch", "TN");

        employeeId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeId, "EMP-TN-001", workLocationId);
        PayRunTestSchema.insertBank(TENANT_A, employeeId);
        seedStatutoryProfile(TENANT_A, employeeId, false, false, false, true);

        seedSimpleSalary(TENANT_A, employeeId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Period decides PT: February run has no PT line, March run produces PT line")
    void periodDecidesPtNotTheClock() throws SQLException {
        // 1. February run: TN deduction months are 3 and 9 -> no PT line
        PayRunResponse febRun = payRunService.create(FEB);
        payRunService.lock(febRun.id());
        PayRunResponse computedFeb = worker.computeNow(febRun.id());

        assertThat(computedFeb.status()).isEqualTo(PayRunStatus.COMPUTED);
        EmployeePayRunLinesResponse febLines = payRunService.lines(febRun.id(), employeeId);
        assertThat(febLines.lines()).noneMatch(l -> "PROFESSIONAL_TAX".equals(l.componentCode()));

        // 2. March run: Month 3 is a deduction month for TN -> PT line produced
        PayRunResponse marchRun = payRunService.create(MARCH);
        payRunService.lock(marchRun.id());
        PayRunResponse computedMarch = worker.computeNow(marchRun.id());

        assertThat(computedMarch.status()).isEqualTo(PayRunStatus.COMPUTED);
        EmployeePayRunLinesResponse marchLines = payRunService.lines(marchRun.id(), employeeId);
        List<EmployeePayRunLineResponse> ptLines = marchLines.lines().stream()
                .filter(l -> "PROFESSIONAL_TAX".equals(l.componentCode()))
                .toList();

        assertThat(ptLines).hasSize(1);
        assertThat(ptLines.get(0).amount()).isGreaterThan(java.math.BigDecimal.ZERO);
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
                        VALUES (?, ?, ?, 'TN', 'Employee', 'MALE', DATE '2025-01-01', 'ACTIVE', ?, 'test', 'test')
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

    private static void seedSimpleSalary(UUID tenantId, UUID employeeId) throws SQLException {
        UUID basic = PayRunTestSchema.insertEarningComponent(tenantId, "BASIC", "Basic", false, true, false);
        UUID ctc = PayRunTestSchema.insertSalary(tenantId, employeeId, LocalDate.of(2025, 1, 1));
        PayRunTestSchema.insertStructureLine("employee_earning", tenantId, ctc, basic, "25000.0000", null);
    }
}
