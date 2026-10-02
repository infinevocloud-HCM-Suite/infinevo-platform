package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

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
 * W-30.2 §7 — locking an off-cycle run locks the run, never the month: one lock row with
 * {@code run_ref} and no period lock; the regular run still locks its period afterwards; cancel
 * leaves the run lock in place (W-19 §6).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class OffCyclePayRunLockIT extends AbstractIntegrationTest {

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
    @DisplayName("Lock writes a run lock and no period lock; an untagged input for the month still posts to it")
    void lockLocksTheRunNotTheMonth() throws SQLException {
        PayRunResponse run = payRunService.createOffCycle(LocalDate.of(2026, 4, 15), List.of(employeeId), null);

        PayRunResponse locked = payRunService.lock(run.id());

        assertThat(locked.status()).isEqualTo(PayRunStatus.LOCKED);
        assertThat(PayRunTestSchema.countRunLocks(TENANT_A, run.id())).isEqualTo(1);
        assertThat(PayRunTestSchema.countPeriodLocks(TENANT_A, "2026-04")).isZero();
        PayInputResponse untagged = payInputService.record(new PayInputCommand(
                employeeId, APRIL, PayInputKind.OVERTIME, null, Money.of("300"), "hrms", "ot-april"));
        assertThat(untagged.postedPeriod()).isEqualTo(APRIL);
    }

    @Test
    @DisplayName("The regular run's lock afterwards succeeds and writes the period lock")
    void regularLockAfterOffCycleLock() throws SQLException {
        PayRunResponse offCycle = payRunService.createOffCycle(LocalDate.of(2026, 4, 15), List.of(employeeId), null);
        payRunService.lock(offCycle.id());
        PayRunResponse regular = payRunService.create(APRIL);

        PayRunResponse locked = payRunService.lock(regular.id());

        assertThat(locked.status()).isEqualTo(PayRunStatus.LOCKED);
        assertThat(PayRunTestSchema.countPeriodLocks(TENANT_A, "2026-04")).isEqualTo(1);
        assertThat(PayRunTestSchema.countRunLocks(TENANT_A, offCycle.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Cancel after lock leaves the run lock in place")
    void cancelKeepsTheRunLock() throws SQLException {
        PayRunResponse run = payRunService.createOffCycle(LocalDate.of(2026, 4, 15), List.of(employeeId), null);
        payRunService.lock(run.id());

        PayRunResponse cancelled = payRunService.cancel(run.id());

        assertThat(cancelled.status()).isEqualTo(PayRunStatus.CANCELLED);
        assertThat(PayRunTestSchema.countRunLocks(TENANT_A, run.id())).isEqualTo(1);
    }
}
