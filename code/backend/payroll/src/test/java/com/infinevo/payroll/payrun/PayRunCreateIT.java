package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.NoPayScheduleException;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayPeriodResponse;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-29.1 §7 — creating a run: the four dates come from {@code periodFor}; a second run for the period
 * is refused; a cancelled run frees the period; and two concurrent creates leave exactly one
 * non-cancelled run, decided by {@code uk_payrun_tenant_period}, not by the service.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunCreateIT extends AbstractIntegrationTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayPeriodService periodService;

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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Create returns a DRAFT run whose four dates equal periodFor, with its counts")
    void createCopiesTheFourDates() throws SQLException {
        givenSchedule();
        PayRunTestSchema.insertPayableEmployee(TENANT_A, "C-01");
        PayPeriodResponse dates = periodService.periodFor(APRIL);

        PayRunResponse run = payRunService.create(APRIL);

        assertThat(run.status()).isEqualTo(PayRunStatus.DRAFT);
        assertThat(run.runType()).isEqualTo(PayRunType.REGULAR);
        assertThat(run.period()).isEqualTo("2026-04");
        assertThat(run.periodStart()).isEqualTo(dates.start());
        assertThat(run.periodEnd()).isEqualTo(dates.end());
        assertThat(run.cutoffDate()).isEqualTo(dates.cutoffDate());
        assertThat(run.payDate()).isEqualTo(dates.payDate());
        assertThat(run.includedCount()).isEqualTo(1);
        assertThat(run.skippedCount()).isZero();
        assertThat(payRunService.get(run.id())).isEqualTo(run);
    }

    @Test
    @DisplayName("A second create for the same period is refused while the first is not cancelled")
    void secondCreateIsRefused() throws SQLException {
        givenSchedule();
        payRunService.create(APRIL);

        assertThatThrownBy(() -> payRunService.create(APRIL)).isInstanceOf(DuplicatePayRunException.class);
        assertThat(PayRunTestSchema.countRuns(TENANT_A, "2026-04", false)).isEqualTo(1);
    }

    @Test
    @DisplayName("After cancel, a new create for the same period succeeds")
    void cancelFreesThePeriod() throws SQLException {
        givenSchedule();
        PayRunResponse first = payRunService.create(APRIL);
        payRunService.cancel(first.id());

        PayRunResponse second = payRunService.create(APRIL);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(PayRunTestSchema.countRuns(TENANT_A, "2026-04", false)).isEqualTo(2);
        assertThat(PayRunTestSchema.countRuns(TENANT_A, "2026-04", true)).isEqualTo(1);
    }

    @Test
    @DisplayName("Two concurrent creates for one period leave exactly one non-cancelled run")
    void concurrentCreatesLeaveOneRun() throws Exception {
        givenSchedule();
        for (int i = 0; i < 20; i++) {
            PayRunTestSchema.insertPayableEmployee(TENANT_A, "R-" + i);
        }
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> create = () -> {
            TenantContext.set(TENANT_A);
            try {
                start.await(10, TimeUnit.SECONDS);
                return payRunService.create(APRIL);
            } catch (DuplicatePayRunException e) {
                return e;
            } finally {
                TenantContext.clear();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Object> outcomes = new ArrayList<>();
        try {
            Future<Object> a = pool.submit(create);
            Future<Object> b = pool.submit(create);
            start.countDown();
            outcomes.add(get(a));
            outcomes.add(get(b));
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).filteredOn(PayRunResponse.class::isInstance).hasSize(1);
        assertThat(outcomes)
                .filteredOn(DuplicatePayRunException.class::isInstance)
                .hasSize(1);
        assertThat(PayRunTestSchema.countRuns(TENANT_A, "2026-04", true)).isEqualTo(1);
    }

    @Test
    @DisplayName("No pay schedule is NoPayScheduleException (409); a period before the first one is 400")
    void scheduleErrors() {
        assertThatThrownBy(() -> payRunService.create(APRIL)).isInstanceOf(NoPayScheduleException.class);

        givenSchedule();
        assertThatThrownBy(() -> payRunService.create(YearMonth.of(2025, 12)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void givenSchedule() {
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
    }

    private static Object get(Future<Object> future) throws InterruptedException {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new AssertionError("create failed with something other than a duplicate", e);
        }
    }
}
