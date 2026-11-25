package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputLockStage;
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
 * D-151 against real Postgres (V223's policy, as app_user): cancelling an approved regular run reopens its
 * CLOSED month, so an entry posts there again and a new regular run can be created, computed and approved.
 * The D-144 rule still holds on the new run: an entry after its compute blocks approval until a recompute.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunCancelReopensMonthIT extends AbstractIntegrationTest {

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
    @DisplayName("Approved then cancelled: the month reopens, takes entries, and a new run is created and approved")
    void cancelAfterApprovalReopensTheMonth() {
        PayRunResponse first = computedJuly();
        payRunService.approve(first.id());
        assertThat(payInputService.periodStage(JULY)).contains(PayInputLockStage.CLOSED);
        // While CLOSED, an entry for July is moved on to the next open month.
        assertThat(deduction("closed-" + UUID.randomUUID()).postedPeriod()).isEqualTo(JULY.plusMonths(1));

        PayRunResponse cancelled = payRunService.cancel(first.id());

        assertThat(cancelled.status()).isEqualTo(PayRunStatus.CANCELLED);
        assertThat(payInputService.periodStage(JULY)).isEmpty();
        assertThat(deduction("reopened-" + UUID.randomUUID()).postedPeriod()).isEqualTo(JULY);

        PayRunResponse second = computedJuly();
        assertThat(second.id()).isNotEqualTo(first.id());
        // D-144 still holds on the new run.
        deduction("late-" + UUID.randomUUID());
        assertThatThrownBy(() -> payRunService.approve(second.id())).isInstanceOf(PayRunInputsChangedException.class);
        worker.computeNow(second.id());
        assertThat(payRunService.approve(second.id()).status()).isEqualTo(PayRunStatus.APPROVED);
        assertThat(payInputService.periodStage(JULY)).contains(PayInputLockStage.CLOSED);
    }

    @Test
    @DisplayName("A cancelled locked run removes its LOCKED lock as before")
    void cancelLockedRunStillUnlocks() {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        assertThat(payInputService.periodStage(JULY)).contains(PayInputLockStage.LOCKED);

        payRunService.cancel(run.id());

        assertThat(payInputService.periodStage(JULY)).isEmpty();
    }
}
