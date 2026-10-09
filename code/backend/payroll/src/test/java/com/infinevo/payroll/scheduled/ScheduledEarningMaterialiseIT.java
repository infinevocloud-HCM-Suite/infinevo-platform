package com.infinevo.payroll.scheduled;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeTerminatedEvent;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.PayRunService;
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
import java.time.ZoneOffset;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-73.6 §7 — {@code ScheduledEarningMaterialiseIT}: a run created for the month writes one pay
 * input per due schedule; a second materialisation writes none; the last instalment sets
 * {@code PAID}; terminating the employee cancels what is left.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ScheduledEarningMaterialiseIT extends AbstractIntegrationTest {

    /** The first period a schedule may name is this month; the run is for next month. */
    private static final YearMonth THIS_MONTH = YearMonth.now(ZoneOffset.UTC);

    private static final YearMonth NEXT_MONTH = THIS_MONTH.plusMonths(1);

    @Autowired
    private ScheduledEarningService scheduledEarnings;

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayInputService payInputService;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PayScheduleService scheduleService;

    private UUID employee;
    private UUID bonus;
    private UUID plainEarning;

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
        employee = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-01", catalogue);
        bonus = PayRunTestSchema.insertEarningComponent(TENANT_A, "SCHED_BONUS", "Bonus", true, true, false);
        flagScheduled(bonus);
        plainEarning = catalogue.special();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Run created for next month: one ONE_TIME_PAYOUT pay input, row PAID; this month has nothing")
    void runCreatedWritesOnePayInput() {
        ScheduledEarningResponse scheduled = scheduledEarnings.create(
                employee,
                new ScheduledEarningRequest(bonus, new BigDecimal("30000"), NEXT_MONTH.toString(), 1, "Diwali"));
        assertThat(scheduled.status()).isEqualTo(ScheduledEarningStatus.SCHEDULED);
        assertThat(scheduled.nextPeriod()).isEqualTo(NEXT_MONTH.toString());

        payRunService.create(NEXT_MONTH);

        List<PayInputResponse> inputs =
                payInputService.forEmployee(employee, NEXT_MONTH).rows();
        assertThat(inputs).hasSize(1);
        PayInputResponse input = inputs.get(0);
        assertThat(input.kind()).isEqualTo(PayInputKind.ONE_TIME_PAYOUT);
        assertThat(input.amount()).isEqualByComparingTo("30000.00");
        assertThat(input.sourceModule()).isEqualTo("payroll");
        assertThat(input.sourceRef()).isEqualTo(ScheduledEarningInstalments.sourceRef(scheduled.id(), NEXT_MONTH));
        assertThat(payInputService.forEmployee(employee, THIS_MONTH).rows()).isEmpty();

        ScheduledEarningResponse after =
                scheduledEarnings.listForEmployee(employee).get(0);
        assertThat(after.status()).isEqualTo(ScheduledEarningStatus.PAID);
        assertThat(after.paidInstalments()).isEqualTo(1);
        assertThat(after.payInputIds()).containsExactly(input.id());
        assertThat(after.nextPeriod()).isNull();
    }

    @Test
    @DisplayName("Materialising the same period again writes nothing — idempotent on (schedule, period)")
    void secondMaterialisationWritesNothing() {
        scheduledEarnings.create(
                employee, new ScheduledEarningRequest(bonus, new BigDecimal("30000"), NEXT_MONTH.toString(), 1, null));

        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isEqualTo(1);
        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isZero();
        assertThat(payInputService.forEmployee(employee, NEXT_MONTH).rows()).hasSize(1);

        // Even with the counter rolled back, the ledger row keeps a retry from paying twice.
        resetCounter();
        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isZero();
        assertThat(payInputService.forEmployee(employee, NEXT_MONTH).rows()).hasSize(1);
        assertThat(scheduledEarnings.listForEmployee(employee).get(0).status()).isEqualTo(ScheduledEarningStatus.PAID);
    }

    @Test
    @DisplayName("Three instalments over three months: 3,333.33 · 3,333.33 · 3,333.34, PAID after the last")
    void threeInstalments() {
        ScheduledEarningResponse scheduled = scheduledEarnings.create(
                employee, new ScheduledEarningRequest(bonus, new BigDecimal("10000"), NEXT_MONTH.toString(), 3, null));

        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isEqualTo(1);
        assertThat(amountIn(NEXT_MONTH)).isEqualByComparingTo("3333.33");
        ScheduledEarningResponse afterOne =
                scheduledEarnings.listForEmployee(employee).get(0);
        assertThat(afterOne.status()).isEqualTo(ScheduledEarningStatus.SCHEDULED);
        assertThat(afterOne.paidInstalments()).isEqualTo(1);
        assertThat(afterOne.nextPeriod()).isEqualTo(NEXT_MONTH.plusMonths(1).toString());

        // The month after is not due yet when the first month is asked for again.
        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isZero();

        assertThat(scheduledEarnings.materialise(NEXT_MONTH.plusMonths(1))).isEqualTo(1);
        assertThat(amountIn(NEXT_MONTH.plusMonths(1))).isEqualByComparingTo("3333.33");
        assertThat(scheduledEarnings.materialise(NEXT_MONTH.plusMonths(2))).isEqualTo(1);
        assertThat(amountIn(NEXT_MONTH.plusMonths(2))).isEqualByComparingTo("3333.34");

        ScheduledEarningResponse done =
                scheduledEarnings.listForEmployee(employee).get(0);
        assertThat(done.id()).isEqualTo(scheduled.id());
        assertThat(done.status()).isEqualTo(ScheduledEarningStatus.PAID);
        assertThat(done.paidInstalments()).isEqualTo(3);
        assertThat(done.payInputIds()).hasSize(3);
        assertThat(scheduledEarnings.materialise(NEXT_MONTH.plusMonths(3))).isZero();
    }

    @Test
    @DisplayName("Terminating the employee cancels the remaining instalments with reason 'terminated'")
    void terminationCancelsTheRest() {
        scheduledEarnings.create(
                employee, new ScheduledEarningRequest(bonus, new BigDecimal("10000"), NEXT_MONTH.toString(), 3, null));
        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isEqualTo(1);

        // PayrollTestApp stubs EmployeeService, so the event core publishes on the transition
        // (EmployeeServiceImplTest proves that) is raised here the way core raises it: in a transaction.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        publisher.publishEvent(new EmployeeTerminatedEvent(TENANT_A, employee, NEXT_MONTH.atDay(15))));

        ScheduledEarningResponse row =
                scheduledEarnings.listForEmployee(employee).get(0);
        assertThat(row.status()).isEqualTo(ScheduledEarningStatus.CANCELLED);
        assertThat(row.statusReason()).isEqualTo("terminated");
        assertThat(row.paidInstalments()).isEqualTo(1);
        assertThat(scheduledEarnings.materialise(NEXT_MONTH.plusMonths(1))).isZero();
        assertThat(payInputService
                        .forEmployee(employee, NEXT_MONTH.plusMonths(1))
                        .rows())
                .isEmpty();
    }

    @Test
    @DisplayName("Paused rows are skipped, resumed rows pay; cancel after PAID and pause of a PAUSED row are refused")
    void pauseResumeCancelRules() {
        ScheduledEarningResponse row = scheduledEarnings.create(
                employee, new ScheduledEarningRequest(bonus, new BigDecimal("5000"), NEXT_MONTH.toString(), 2, null));

        assertThat(scheduledEarnings.pause(row.id(), "hold").status()).isEqualTo(ScheduledEarningStatus.PAUSED);
        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isZero();
        assertThatThrownBy(() -> scheduledEarnings.pause(row.id(), "again"))
                .isInstanceOf(ScheduledEarningService.IllegalTransitionException.class);

        assertThat(scheduledEarnings.resume(row.id()).status()).isEqualTo(ScheduledEarningStatus.SCHEDULED);
        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isEqualTo(1);
        assertThat(scheduledEarnings.cancel(row.id(), "changed my mind").status())
                .isEqualTo(ScheduledEarningStatus.CANCELLED);
        assertThat(scheduledEarnings.materialise(NEXT_MONTH.plusMonths(1))).isZero();
        assertThatThrownBy(() -> scheduledEarnings.cancel(row.id(), null))
                .isInstanceOf(ScheduledEarningService.IllegalTransitionException.class);
        assertThatThrownBy(() -> scheduledEarnings.resume(row.id()))
                .isInstanceOf(ScheduledEarningService.IllegalTransitionException.class);
    }

    @Test
    @DisplayName("A past month, a component without the Scheduled flag, 13 instalments or a zero amount are refused")
    void createRules() {
        assertThatThrownBy(() -> scheduledEarnings.create(
                        employee,
                        new ScheduledEarningRequest(
                                bonus,
                                new BigDecimal("100"),
                                THIS_MONTH.minusMonths(1).toString(),
                                1,
                                null)))
                .isInstanceOf(ScheduledEarningService.ValidationException.class)
                .satisfies(e -> assertThat(((ScheduledEarningService.ValidationException) e).fieldErrors())
                        .containsKey("firstPeriod"));
        assertThatThrownBy(() -> scheduledEarnings.create(
                        employee,
                        new ScheduledEarningRequest(
                                plainEarning, new BigDecimal("100"), NEXT_MONTH.toString(), 1, null)))
                .isInstanceOf(ScheduledEarningService.ValidationException.class)
                .satisfies(e -> assertThat(((ScheduledEarningService.ValidationException) e).fieldErrors())
                        .containsKey("componentId"));
        assertThatThrownBy(() -> scheduledEarnings.create(
                        employee,
                        new ScheduledEarningRequest(bonus, new BigDecimal("100"), NEXT_MONTH.toString(), 13, null)))
                .isInstanceOf(ScheduledEarningService.ValidationException.class)
                .satisfies(e -> assertThat(((ScheduledEarningService.ValidationException) e).fieldErrors())
                        .containsKey("instalments"));
        assertThatThrownBy(() -> scheduledEarnings.create(
                        employee, new ScheduledEarningRequest(bonus, BigDecimal.ZERO, NEXT_MONTH.toString(), 1, null)))
                .isInstanceOf(ScheduledEarningService.ValidationException.class)
                .satisfies(e -> assertThat(((ScheduledEarningService.ValidationException) e).fieldErrors())
                        .containsKey("amount"));
        assertThat(scheduledEarnings.listForEmployee(employee)).isEmpty();
    }

    @Test
    @DisplayName(
            "F-1: paused in month M, resumed in M+1 - materialising M+1 pays the overdue instalment into M+1, once")
    void overdueInstalmentIsPaidLate() {
        ScheduledEarningResponse row = scheduledEarnings.create(
                employee, new ScheduledEarningRequest(bonus, new BigDecimal("10000"), NEXT_MONTH.toString(), 3, null));
        scheduledEarnings.pause(row.id(), "hold");
        assertThat(scheduledEarnings.materialise(NEXT_MONTH)).isZero();
        scheduledEarnings.resume(row.id());

        YearMonth later = NEXT_MONTH.plusMonths(1);
        assertThat(scheduledEarnings.materialise(later)).isEqualTo(1);
        assertThat(amountIn(later)).isEqualByComparingTo("3333.33");
        assertThat(payInputService.forEmployee(employee, NEXT_MONTH).rows()).isEmpty();
        // One instalment per period: asking again for the same month pays nothing more.
        assertThat(scheduledEarnings.materialise(later)).isZero();

        ScheduledEarningResponse after =
                scheduledEarnings.listForEmployee(employee).get(0);
        assertThat(after.paidInstalments()).isEqualTo(1);
        assertThat(after.status()).isEqualTo(ScheduledEarningStatus.SCHEDULED);
        assertThat(after.nextPeriod()).isEqualTo(later.plusMonths(1).toString());
        assertThat(after.payInputIds()).hasSize(1);
    }

    @Test
    @DisplayName("F-3: one row the ledger refuses is skipped; the other row is paid and the pay run is still created")
    void oneBadRowDoesNotBlockTheOthers() throws SQLException {
        // A row create() now refuses - 0.05 over 12 - written straight to the table, as an older row might be.
        UUID bad = insertRawSchedule(new BigDecimal("0.05"), 12);
        ScheduledEarningResponse good = scheduledEarnings.create(
                employee, new ScheduledEarningRequest(bonus, new BigDecimal("30000"), NEXT_MONTH.toString(), 1, null));

        assertThat(payRunService.create(NEXT_MONTH)).isNotNull();

        List<PayInputResponse> inputs =
                payInputService.forEmployee(employee, NEXT_MONTH).rows();
        assertThat(inputs).hasSize(1);
        assertThat(inputs.get(0).amount()).isEqualByComparingTo("30000.00");
        List<ScheduledEarningResponse> rows = scheduledEarnings.listForEmployee(employee);
        assertThat(rows)
                .filteredOn(r -> r.id().equals(good.id()))
                .singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(ScheduledEarningStatus.PAID));
        assertThat(rows).filteredOn(r -> r.id().equals(bad)).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(ScheduledEarningStatus.SCHEDULED);
            assertThat(r.paidInstalments()).isZero();
        });
    }

    @Test
    @DisplayName("F-3, F-4: an amount too small for its instalments, or with more than 2 decimals, is refused")
    void amountMustSplitIntoPaise() {
        assertThatThrownBy(() -> scheduledEarnings.create(
                        employee,
                        new ScheduledEarningRequest(bonus, new BigDecimal("0.05"), NEXT_MONTH.toString(), 12, null)))
                .isInstanceOf(ScheduledEarningService.ValidationException.class)
                .satisfies(e -> assertThat(((ScheduledEarningService.ValidationException) e).fieldErrors())
                        .containsKey("amount"));
        assertThatThrownBy(() -> scheduledEarnings.create(
                        employee,
                        new ScheduledEarningRequest(bonus, new BigDecimal("0.10"), NEXT_MONTH.toString(), 12, null)))
                .isInstanceOf(ScheduledEarningService.ValidationException.class);
        assertThatThrownBy(() -> scheduledEarnings.create(
                        employee,
                        new ScheduledEarningRequest(bonus, new BigDecimal("100.005"), NEXT_MONTH.toString(), 1, null)))
                .isInstanceOf(ScheduledEarningService.ValidationException.class)
                .satisfies(e -> assertThat(((ScheduledEarningService.ValidationException) e).fieldErrors())
                        .containsEntry("amount", "amount can have at most 2 decimal places"));
        assertThat(scheduledEarnings.listForEmployee(employee)).isEmpty();

        // Trailing zeros are not finer than paise: 100.5000 is 100.50.
        assertThat(scheduledEarnings
                        .create(
                                employee,
                                new ScheduledEarningRequest(
                                        bonus, new BigDecimal("100.5000"), NEXT_MONTH.toString(), 1, null))
                        .status())
                .isEqualTo(ScheduledEarningStatus.SCHEDULED);
    }

    private UUID insertRawSchedule(BigDecimal amount, int instalments) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.scheduled_earning (tenant_id, employee_id, earning_id, amount, first_period,"
                                + " instalments) VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employee);
            ps.setObject(3, bonus);
            ps.setBigDecimal(4, amount);
            ps.setObject(5, NEXT_MONTH.atDay(1));
            ps.setInt(6, instalments);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private BigDecimal amountIn(YearMonth period) {
        List<PayInputResponse> rows =
                payInputService.forEmployee(employee, period).rows();
        assertThat(rows).hasSize(1);
        return rows.get(0).amount();
    }

    private static void flagScheduled(UUID earningId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE payroll.earning SET is_scheduled_earning = true WHERE id = ?")) {
            ps.setObject(1, earningId);
            ps.executeUpdate();
        }
    }

    /** Simulates a counter that did not advance although the ledger row was written. */
    private static void resetCounter() {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE payroll.scheduled_earning SET paid_instalments = 0, last_paid_period = NULL,"
                                + " status = 'SCHEDULED'")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
