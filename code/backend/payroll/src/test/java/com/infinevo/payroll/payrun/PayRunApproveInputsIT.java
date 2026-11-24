package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
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
 * D-144 against real Postgres: a computed month still takes entries, the run detail counts them,
 * approval is refused until the run is computed again, and the recompute clears the count. A row
 * and its own reversal both written after the compute count as nothing.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunApproveInputsIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayInputService payInputService;

    private UUID employeeId;

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
        employeeId = PayRunTestSchema.insertWorkedExampleEmployee(
                TENANT_A, "A-01", PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PayRunResponse computedJuly() {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        PayRunResponse computed = worker.computeNow(run.id());
        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        return computed;
    }

    private PayInputResponse deduction(String ref) {
        return payInputService.record(new PayInputCommand(
                employeeId, JULY, PayInputKind.AD_HOC_DEDUCTION, null, Money.of("500"), "payroll", ref));
    }

    @Test
    @DisplayName("An entry after the compute posts into the month, is counted, and blocks approval until recompute")
    void entryAfterComputeBlocksApproveUntilRecompute() {
        PayRunResponse run = computedJuly();
        assertThat(payRunService.get(run.id()).inputsSinceCompute()).isZero();

        PayInputResponse late = deduction("late-" + UUID.randomUUID());
        assertThat(late.postedPeriod()).isEqualTo(JULY);
        assertThat(payRunService.get(run.id()).inputsSinceCompute()).isEqualTo(1L);

        assertThatThrownBy(() -> payRunService.approve(run.id()))
                .isInstanceOf(PayRunInputsChangedException.class)
                .hasMessage("1 new pay input since this run was computed. Compute again before approving.");
        assertThat(payRunService.get(run.id()).status()).isEqualTo(PayRunStatus.COMPUTED);

        PayRunResponse recomputed = worker.computeNow(run.id());
        assertThat(recomputed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(payRunService.get(run.id()).inputsSinceCompute()).isZero();

        PayRunResponse approved = payRunService.approve(run.id());
        assertThat(approved.status()).isEqualTo(PayRunStatus.APPROVED);
    }

    @Test
    @DisplayName("A row and its own reversal both after the compute count as nothing; approval goes through")
    void entryAndItsReversalNetToNothing() {
        PayRunResponse run = computedJuly();

        PayInputResponse late = deduction("late-" + UUID.randomUUID());
        payInputService.reverse(late.id(), "entered by mistake");

        assertThat(payRunService.get(run.id()).inputsSinceCompute()).isZero();
        assertThat(payRunService.approve(run.id()).status()).isEqualTo(PayRunStatus.APPROVED);
    }

    @Test
    @DisplayName("A reversal after the compute of a row the run paid is counted")
    void reversalOfAPaidRowIsCounted() {
        PayRunResponse run = payRunService.create(JULY);
        PayInputResponse early = deduction("early-" + UUID.randomUUID());
        payRunService.lock(run.id());
        worker.computeNow(run.id());

        payInputService.reverse(early.id(), "recovered in cash");

        assertThat(payRunService.get(run.id()).inputsSinceCompute()).isEqualTo(1L);
        assertThatThrownBy(() -> payRunService.approve(run.id())).isInstanceOf(PayRunInputsChangedException.class);
    }
}
