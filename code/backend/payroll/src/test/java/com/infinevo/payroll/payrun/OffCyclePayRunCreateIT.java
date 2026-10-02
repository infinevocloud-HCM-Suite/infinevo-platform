package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.schedule.NoPayScheduleException;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayPeriodResponse;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * W-30.2 §7 — creating an off-cycle run: the period is the pay date's month with the schedule's dates
 * and the officer's pay date; any number per month; the regular run for that month is still created
 * once and only once — {@code uk_payrun_tenant_period} narrowed, not dropped (BUG-010).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class OffCyclePayRunCreateIT extends AbstractIntegrationTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final LocalDate MID_APRIL = LocalDate.of(2026, 4, 15);

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
    @DisplayName("Create: OFF_CYCLE, the pay date's month and periodFor's dates, the officer's pay date and note")
    void createUsesThePayDatesMonth() throws SQLException {
        givenSchedule();
        UUID payable = PayRunTestSchema.insertPayableEmployee(TENANT_A, "O-01");
        UUID noBank = PayRunTestSchema.insertEmployee(TENANT_A, "O-02", LocalDate.of(2025, 1, 1), "ACTIVE", null);
        PayRunTestSchema.insertSalary(TENANT_A, noBank, LocalDate.of(2025, 1, 1));
        UUID noSalary = PayRunTestSchema.insertEmployee(TENANT_A, "O-03", LocalDate.of(2026, 4, 10), "ACTIVE", null);
        PayRunTestSchema.insertBank(TENANT_A, noSalary);
        PayPeriodResponse dates = periodService.periodFor(APRIL);

        PayRunResponse run =
                payRunService.createOffCycle(MID_APRIL, List.of(payable, noBank, noSalary, payable), "  Diwali bonus ");

        assertThat(run.runType()).isEqualTo(PayRunType.OFF_CYCLE);
        assertThat(run.status()).isEqualTo(PayRunStatus.DRAFT);
        assertThat(run.period()).isEqualTo("2026-04");
        assertThat(run.periodStart()).isEqualTo(dates.start());
        assertThat(run.periodEnd()).isEqualTo(dates.end());
        assertThat(run.cutoffDate()).isEqualTo(dates.cutoffDate());
        assertThat(run.payDate()).isEqualTo(MID_APRIL);
        assertThat(run.notes()).isEqualTo("Diwali bonus");
        // Named twice, one row: two included (the joiner with no salary among them), one skipped.
        assertThat(run.includedCount()).isEqualTo(2);
        assertThat(run.skippedCount()).isEqualTo(1);
        assertThat(payRunService.get(run.id())).isEqualTo(run);

        List<EmployeePayRunResponse> rows =
                payRunService.employees(run.id(), null, PageRequest.of(0, 10)).getContent();
        assertThat(rows).hasSize(3);
        assertThat(rows)
                .filteredOn(r -> r.employeeId().equals(noBank))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.inclusionStatus()).isEqualTo(InclusionStatus.SKIPPED);
                    assertThat(r.skipReason()).isEqualTo(SkipReason.NO_BANK_DETAILS);
                });
        assertThat(rows)
                .filteredOn(r -> r.employeeId().equals(noSalary))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.inclusionStatus()).isEqualTo(InclusionStatus.INCLUDED);
                    assertThat(r.salaryVersionId()).isNull();
                });
    }

    @Test
    @DisplayName("Two off-cycle runs for one month are fine; the regular run is still created once, and only once")
    void manyOffCycleOneRegular() throws SQLException {
        givenSchedule();
        UUID employee = PayRunTestSchema.insertPayableEmployee(TENANT_A, "O-01");

        PayRunResponse first = payRunService.createOffCycle(MID_APRIL, List.of(employee), null);
        PayRunResponse second = payRunService.createOffCycle(MID_APRIL.plusDays(5), List.of(employee), null);
        PayRunResponse regular = payRunService.create(APRIL);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(regular.runType()).isEqualTo(PayRunType.REGULAR);
        assertThatThrownBy(() -> payRunService.create(APRIL)).isInstanceOf(DuplicatePayRunException.class);
        assertThat(PayRunTestSchema.countRuns(TENANT_A, "2026-04", true)).isEqualTo(3);

        assertThat(payRunService
                        .list(null, PayRunType.OFF_CYCLE, PageRequest.of(0, 10))
                        .getContent())
                .extracting(PayRunResponse::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(payRunService
                        .list(PayRunStatus.DRAFT, PayRunType.REGULAR, PageRequest.of(0, 10))
                        .getContent())
                .extracting(PayRunResponse::id)
                .containsExactly(regular.id());
        assertThat(payRunService.list(null, PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("With off-cycle runs in the month, two concurrent regular creates still leave exactly one")
    void concurrentRegularCreatesLeaveOne() throws Exception {
        givenSchedule();
        List<UUID> employees = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            employees.add(PayRunTestSchema.insertPayableEmployee(TENANT_A, "R-" + i));
        }
        payRunService.createOffCycle(MID_APRIL, employees, null);
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
        assertThat(PayRunTestSchema.countRuns(TENANT_A, "2026-04", true)).isEqualTo(2);
    }

    @Test
    @DisplayName("Refused: no employees, an unknown or out-of-period employee, no schedule, a date before it")
    void createRefusals() throws SQLException {
        UUID employee = PayRunTestSchema.insertPayableEmployee(TENANT_A, "O-01");
        assertThatThrownBy(() -> payRunService.createOffCycle(MID_APRIL, List.of(employee), null))
                .isInstanceOf(NoPayScheduleException.class);

        givenSchedule();
        assertThatThrownBy(() -> payRunService.createOffCycle(MID_APRIL, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payRunService.createOffCycle(null, List.of(employee), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payRunService.createOffCycle(LocalDate.of(2025, 12, 15), List.of(employee), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payRunService.createOffCycle(MID_APRIL, List.of(employee), "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);

        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> payRunService.createOffCycle(MID_APRIL, List.of(employee, unknown), null))
                .isInstanceOf(EmployeeNotInRunException.class)
                .hasMessageContaining(unknown.toString());
        UUID left = PayRunTestSchema.insertEmployee(
                TENANT_A, "O-09", LocalDate.of(2024, 1, 1), "TERMINATED", LocalDate.of(2026, 3, 31));
        assertThatThrownBy(() -> payRunService.createOffCycle(MID_APRIL, List.of(employee, left), null))
                .isInstanceOf(EmployeeNotInRunException.class)
                .hasMessageContaining(left.toString());

        assertThat(PayRunTestSchema.countRuns(TENANT_A, "2026-04", false)).isZero();
    }

    @Test
    @DisplayName("V061: the run_type CHECK takes both values and nothing else; the unique index is regular-only")
    void migrationShape() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conrelid = 'payroll.payrun'::regclass AND conname = 'ck_payrun_run_type'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains("'REGULAR'").contains("'OFF_CYCLE'");
            }
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM pg_constraint "
                    + "WHERE conrelid = 'payroll.payrun'::regclass AND conname = 'payrun_run_type_check'")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
            try (ResultSet rs = st.executeQuery("SELECT indexdef FROM pg_indexes "
                    + "WHERE schemaname = 'payroll' AND indexname = 'uk_payrun_tenant_period'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1))
                        .startsWith("CREATE UNIQUE INDEX")
                        .contains("(tenant_id, period)")
                        .contains("'CANCELLED'")
                        .contains("'REGULAR'");
            }
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'payroll' "
                    + "AND (table_name LIKE 'off_cycle%' OR table_name = 'one_time_payout')")) {
                rs.next();
                assertThat(rs.getInt(1)).as("zero new tables").isZero();
            }
        }
    }

    private void givenSchedule() {
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
    }

    private static Object get(Future<Object> future) throws InterruptedException {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError("create failed with something other than a duplicate", e);
        }
    }
}
