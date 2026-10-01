package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
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
 * W-30.2 §7 — the acceptance test. A bonus and a deduction added to an off-cycle run are the only
 * lines on it — no structure, no loss of pay — and the regular run for the same month, computed
 * after it, pays neither of them (§9, the expensive risk).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class OffCyclePayRunComputeIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final LocalDate MID_JULY = LocalDate.of(2026, 7, 15);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    private UUID first;
    private UUID second;

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
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        first = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-01", catalogue);
        second = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-02", catalogue);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Off-cycle pays only its inputs; the regular run for the month, computed after, pays neither")
    void bonusReachesTheOffCycleRunAndNoOther() throws SQLException {
        PayRunResponse offCycle = payRunService.createOffCycle(MID_JULY, List.of(first, second), "Bonus");
        payRunService.addInputs(
                offCycle.id(),
                List.of(
                        new PayRunInputRequest(first, PayInputKind.ONE_TIME_PAYOUT, new BigDecimal("10000"), "b-1"),
                        new PayRunInputRequest(second, PayInputKind.AD_HOC_DEDUCTION, new BigDecimal("500"), "d-1")));
        payRunService.lock(offCycle.id());

        PayRunResponse computed = worker.computeNow(offCycle.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(computed.failureReason()).isNull();
        assertThat(payRunService.lines(offCycle.id(), first).lines())
                .extracting(
                        EmployeePayRunLineResponse::source,
                        EmployeePayRunLineResponse::lineKind,
                        EmployeePayRunLineResponse::componentCode,
                        EmployeePayRunLineResponse::amount)
                .containsExactly(
                        tuple(LineSource.PAY_INPUT, LineKind.EARNING, "ONE_TIME_PAYOUT", new BigDecimal("10000.0000")));
        assertThat(payRunService.lines(offCycle.id(), second).lines())
                .extracting(EmployeePayRunLineResponse::lineKind, EmployeePayRunLineResponse::amount)
                .containsExactly(tuple(LineKind.DEDUCTION, new BigDecimal("500.0000")));

        List<EmployeePayRunResponse> rows = payRunService
                .employees(offCycle.id(), InclusionStatus.INCLUDED, PageRequest.of(0, 10))
                .getContent();
        EmployeePayRunResponse firstRow = row(rows, first);
        assertThat(firstRow.netPay()).isEqualByComparingTo("10000");
        assertThat(firstRow.lopDays()).isEqualByComparingTo("0");
        assertThat(firstRow.unpaidDays()).isEqualByComparingTo("0");
        assertThat(firstRow.paidDays()).isEqualByComparingTo("0");
        // W-29.3 keeps a negative net: the deduction is not capped.
        assertThat(row(rows, second).netPay()).isEqualByComparingTo("-500");
        assertThat(computed.totalGross()).isEqualByComparingTo("10000");
        assertThat(computed.totalDeductions()).isEqualByComparingTo("500");
        assertThat(computed.totalNetPay()).isEqualByComparingTo("9500");
        assertThat(computed.negativeNetCount()).isEqualTo(1);
        assertThat(PayRunTestSchema.countLines(TENANT_A, offCycle.id())).isEqualTo(2);

        // The regular run for July: the structure as ever, and neither tagged input.
        PayRunResponse regular = payRunService.create(JULY);
        payRunService.lock(regular.id());
        PayRunResponse regularComputed = worker.computeNow(regular.id());

        assertThat(regularComputed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(regularComputed.totalNetPay()).isEqualByComparingTo("94000.00");
        for (UUID employee : List.of(first, second)) {
            assertThat(payRunService.lines(regular.id(), employee).lines())
                    .extracting(EmployeePayRunLineResponse::source)
                    .doesNotContain(LineSource.PAY_INPUT);
        }
    }

    @Test
    @DisplayName("A joiner with no salary version is still paid, and the figure carries its policy stamp (W-18.2)")
    void noSalaryStillComputes() throws SQLException {
        UUID joiner = PayRunTestSchema.insertEmployee(TENANT_A, "J-01", LocalDate.of(2026, 7, 10), "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, joiner);
        PayRunResponse offCycle = payRunService.createOffCycle(MID_JULY, List.of(joiner), "Joining bonus");
        payRunService.addInputs(
                offCycle.id(),
                List.of(new PayRunInputRequest(joiner, PayInputKind.ONE_TIME_PAYOUT, new BigDecimal("25000"), "j")));
        payRunService.lock(offCycle.id());

        PayRunResponse computed = worker.computeNow(offCycle.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(computed.totalNetPay()).isEqualByComparingTo("25000");
        assertThat(payRunService.lines(offCycle.id(), joiner).computationError())
                .isNull();
    }

    private static EmployeePayRunResponse row(List<EmployeePayRunResponse> rows, UUID employeeId) {
        return rows.stream()
                .filter(r -> r.employeeId().equals(employeeId))
                .findFirst()
                .orElseThrow();
    }
}
