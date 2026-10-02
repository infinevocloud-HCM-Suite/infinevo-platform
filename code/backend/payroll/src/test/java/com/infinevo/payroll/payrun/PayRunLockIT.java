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

/**
 * W-29.1 §7 — lock is W-19's period lock plus {@code LOCKED}: one {@code core.pay_input_period_lock}
 * row, later inputs for the period post to the next one, a second lock is refused, and cancel after
 * lock leaves the lock row in place (W-19 §6).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunLockIT extends AbstractIntegrationTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);

    @Autowired
    private PayRunService payRunService;

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
        employeeId = PayRunTestSchema.insertPayableEmployee(TENANT_A, "L-01");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Lock writes one period lock row and stamps the run LOCKED with locked_at and locked_by")
    void lockWritesThePeriodLock() throws SQLException {
        PayRunResponse run = payRunService.create(APRIL);

        PayRunResponse locked = payRunService.lock(run.id());

        assertThat(locked.status()).isEqualTo(PayRunStatus.LOCKED);
        assertThat(locked.lockedAt()).isNotNull();
        assertThat(locked.lockedBy()).isNotBlank();
        assertThat(PayRunTestSchema.countPeriodLocks(TENANT_A, "2026-04")).isEqualTo(1);
    }

    @Test
    @DisplayName("After lock, a pay input recorded for the period posts to the next period")
    void inputAfterLockPostsToNextPeriod() {
        PayRunResponse run = payRunService.create(APRIL);
        payRunService.lock(run.id());

        PayInputResponse posted = payInputService.record(new PayInputCommand(
                employeeId, APRIL, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "after-lock"));

        assertThat(posted.postedPeriod()).isEqualTo(APRIL.plusMonths(1));
    }

    @Test
    @DisplayName("Lock on a LOCKED run is refused (409) and writes no second lock row")
    void lockTwiceIsRefused() throws SQLException {
        PayRunResponse run = payRunService.create(APRIL);
        payRunService.lock(run.id());

        assertThatThrownBy(() -> payRunService.lock(run.id())).isInstanceOf(IllegalPayRunTransitionException.class);
        assertThat(PayRunTestSchema.countPeriodLocks(TENANT_A, "2026-04")).isEqualTo(1);
    }

    @Test
    @DisplayName("Cancel after lock leaves the period lock row in place")
    void cancelKeepsThePeriodLock() throws SQLException {
        PayRunResponse run = payRunService.create(APRIL);
        payRunService.lock(run.id());

        PayRunResponse cancelled = payRunService.cancel(run.id());

        assertThat(cancelled.status()).isEqualTo(PayRunStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(PayRunTestSchema.countPeriodLocks(TENANT_A, "2026-04")).isEqualTo(1);
    }

    @Test
    @DisplayName("Lock on a CANCELLED run is refused and never touches the period lock")
    void lockAfterCancelIsRefused() throws SQLException {
        PayRunResponse run = payRunService.create(APRIL);
        payRunService.cancel(run.id());

        assertThatThrownBy(() -> payRunService.lock(run.id())).isInstanceOf(IllegalPayRunTransitionException.class);
        assertThat(PayRunTestSchema.countPeriodLocks(TENANT_A, "2026-04")).isZero();
    }
}
