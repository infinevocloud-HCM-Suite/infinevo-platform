package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.data.domain.PageRequest;

/**
 * W-29.3 §7 — a real W-18.1 policy, a LOP_DAYS row written through {@code PayInputService.record},
 * lock, compute: the LOP line and {@code lop_days} match. The same employee set-up in a second tenant
 * with a different basis gets a different line.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunLopIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayInputService payInputService;

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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ACTUAL_DAYS: 1.5 LOP days on a 42,500 pro-rata base in July is 42,500 × 1.5 ÷ 31 = 2,056.45")
    void lopLineUnderActualDays() throws SQLException {
        UUID employee = employeeWithLopDays(TENANT_A, "1.5");

        PayRunResponse run = computeJuly();

        EmployeePayRunLinesResponse lines = payRunService.lines(run.id(), employee);
        assertThat(lines.lines())
                .filteredOn(l -> l.source() == LineSource.LOP && l.lineKind() == LineKind.DEDUCTION)
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.componentCode()).isEqualTo("LOP");
                    assertThat(l.amount()).isEqualByComparingTo("2056.45");
                });
        EmployeePayRunResponse row = row(run, employee);
        assertThat(row.lopDays()).isEqualByComparingTo("1.50");
        assertThat(row.unpaidDays()).isEqualByComparingTo("1.50");
        assertThat(row.paidDays()).isEqualByComparingTo("29.50");
        assertThat(row.totalDeductions()).isEqualByComparingTo("2056.45");
        // 45,000 gross + 2,000 reimbursement − 2,056.45
        assertThat(row.netPay()).isEqualByComparingTo("44943.55");
    }

    @Test
    @DisplayName("The same employee in a tenant on FIXED_30 gets 42,500 × 1.5 ÷ 30 = 2,125.00")
    void differentBasisDifferentLine() throws SQLException {
        // Tenant A has an employee and LOP days of its own in the same period; none of it may reach B's run.
        employeeWithLopDays(TENANT_A, "5");
        PayRunTestSchema.setPolicy(TENANT_B, "FIXED_30", true, true, "HALF_UP_2");
        UUID employee = employeeWithLopDays(TENANT_B, "1.5");

        TenantContext.set(TENANT_B);
        PayRunResponse run = computeJuly();

        assertThat(payRunService
                        .employees(run.id(), null, PageRequest.of(0, 10))
                        .getContent())
                .as("only tenant B's employee is in tenant B's run")
                .extracting(EmployeePayRunResponse::employeeId)
                .containsExactly(employee);
        assertThat(row(run, employee).lopDays()).isEqualByComparingTo("1.50");

        assertThat(payRunService.lines(run.id(), employee).lines())
                .filteredOn(l -> l.source() == LineSource.LOP && l.lineKind() == LineKind.DEDUCTION)
                .singleElement()
                .satisfies(l -> assertThat(l.amount()).isEqualByComparingTo("2125.00"));
        assertThat(row(run, employee).paidDays()).isEqualByComparingTo("28.50");
    }

    @Test
    @DisplayName(
            "LOP deducted in a locked June and reversed afterwards is paid back in July: 42,500 × 2 ÷ 31 = 2,741.94")
    void reversalOfALockedMonthIsRefundedInTheNext() throws SQLException {
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        UUID employee = PayRunTestSchema.insertWorkedExampleEmployee(
                TENANT_A, "L-02", PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A));
        PayRunTestSchema.markWorkedExampleProRata(TENANT_A);
        YearMonth june = YearMonth.of(2026, 6);
        PayInputResponse lop = payInputService.record(new PayInputCommand(
                employee,
                june,
                PayInputKind.LOP_DAYS,
                new BigDecimal("2"),
                null,
                "hrms",
                "leave-" + UUID.randomUUID()));
        PayRunResponse juneRun = payRunService.create(june);
        payRunService.lock(juneRun.id());

        // The leave is cancelled after June is locked: W-19 posts the reversal to the next open period.
        PayInputResponse reversal = payInputService.reverse(lop.id(), "leave cancelled");
        assertThat(reversal.postedPeriod()).isEqualTo(JULY);

        PayRunResponse run = computeJuly();

        assertThat(payRunService.lines(run.id(), employee).lines())
                .filteredOn(l -> l.source() == LineSource.LOP)
                .extracting(
                        EmployeePayRunLineResponse::lineKind, EmployeePayRunLineResponse::componentCode, l -> l.amount()
                                .setScale(2, RoundingMode.UNNECESSARY))
                .containsExactly(
                        tuple(LineKind.EARNING, "LOP_REVERSAL", new BigDecimal("2741.94")),
                        // Employer PF 1,800 × 2 ÷ 31
                        tuple(LineKind.BENEFIT, "LOP_BENEFIT_REVERSAL", new BigDecimal("116.13")));
        EmployeePayRunResponse row = row(run, employee);
        assertThat(row.lopDays()).isEqualByComparingTo("0");
        assertThat(row.unpaidDays()).isEqualByComparingTo("0");
        assertThat(row.paidDays()).isEqualByComparingTo("31");
        assertThat(row.totalDeductions()).isEqualByComparingTo("0");
        // 45,000 gross + 2,741.94 refund + 2,000 reimbursement
        assertThat(row.netPay()).isEqualByComparingTo("49741.94");
    }

    @Test
    @DisplayName("A variable earning flagged pro-rata is not scaled: the base is 35,000, not 42,500")
    void variableEarningIsNeverScaled() throws SQLException {
        UUID employee = employeeWithLopDays(TENANT_A, "1.5");
        // Special allowance becomes a monthly variable earning that still carries the pro-rata flag.
        PayRunTestSchema.execute(
                "UPDATE payroll.earning SET is_variable = true, earning_frequency = 'MONTHLY'"
                        + " WHERE tenant_id = ? AND code = 'SPECIAL'",
                TENANT_A);

        PayRunResponse run = computeJuly();

        // (25,000 + 10,000) × 1.5 ÷ 31
        assertThat(payRunService.lines(run.id(), employee).lines())
                .filteredOn(l -> l.source() == LineSource.LOP && l.lineKind() == LineKind.DEDUCTION)
                .singleElement()
                .satisfies(l -> assertThat(l.amount()).isEqualByComparingTo("1693.55"));
    }

    private UUID employeeWithLopDays(UUID tenant, String days) throws SQLException {
        TenantContext.set(tenant);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        UUID employee = PayRunTestSchema.insertWorkedExampleEmployee(
                tenant, "L-01", PayRunTestSchema.insertWorkedExampleCatalogue(tenant));
        PayRunTestSchema.markWorkedExampleProRata(tenant);
        payInputService.record(new PayInputCommand(
                employee,
                JULY,
                PayInputKind.LOP_DAYS,
                new BigDecimal(days),
                null,
                "hrms",
                "leave-" + UUID.randomUUID()));
        return employee;
    }

    private PayRunResponse computeJuly() {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        PayRunResponse computed = worker.computeNow(run.id());
        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        return computed;
    }

    private EmployeePayRunResponse row(PayRunResponse run, UUID employee) {
        return payRunService.employees(run.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10)).getContent().stream()
                .filter(r -> r.employeeId().equals(employee))
                .findFirst()
                .orElseThrow();
    }
}
