package com.infinevo.core.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-39.2 §7 — the ledger side of overtime capture, against a real {@code PayInputService}, not a
 * stand-in: one {@code OVERTIME} row per recorded entry, a reversal on cancel, and no orphaned
 * overtime row when the ledger itself refuses the write.
 */
@SpringBootTest(classes = OvertimeTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            OvertimeTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class OvertimeLedgerIT extends AbstractIntegrationTest {

    @Autowired
    private OvertimeService overtimeService;

    @Autowired
    private com.infinevo.core.payinput.PayInputService payInputService;

    private UUID tenant;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = OvertimeTestSchema.insertTenant("Ledger " + UUID.randomUUID());
        employee = OvertimeTestSchema.insertEmployee(tenant, "OT-LEDGER-" + UUID.randomUUID());
        TenantContext.set(tenant);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST leaves exactly one core.pay_input OVERTIME row for the employee and period")
    void recordLeavesExactlyOneLedgerRow() {
        OvertimeResponse response = overtimeService.record(
                new OvertimeEntry(employee, LocalDate.of(2026, 4, 10), new BigDecimal("3.00"), null, "late shift"));

        assertThat(response.payInputId()).isNotNull();
        assertThat(response.postedPeriod()).isEqualTo(YearMonth.of(2026, 4));

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).hasSize(1);
        assertThat(ledger.rows().get(0).id()).isEqualTo(response.payInputId());
        assertThat(ledger.rows().get(0).kind()).isEqualTo(com.infinevo.core.payinput.PayInputKind.OVERTIME);
        assertThat(ledger.rows().get(0).sourceRef()).isEqualTo("overtime_request:" + response.id());
    }

    @Test
    @DisplayName("DELETE leaves a second ledger row with reverses_id set, and the two net to zero")
    void cancelLeavesAReversalThatNetsToZero() {
        OvertimeResponse recorded = overtimeService.record(
                new OvertimeEntry(employee, LocalDate.of(2026, 4, 11), new BigDecimal("2.00"), null, null));

        overtimeService.cancel(recorded.id());

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).hasSize(2);
        assertThat(ledger.rows()).anySatisfy(row -> assertThat(row.reversesId()).isEqualTo(recorded.payInputId()));
        assertThat(ledger.quantityTotalsByKind().get(com.infinevo.core.payinput.PayInputKind.OVERTIME))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("A POST whose ledger write is refused leaves no overtime row at all")
    void aRefusedLedgerWriteLeavesNoOvertimeRow() throws SQLException {
        // Exhausts the ledger's late-input redirect search (W-19, MAX_REDIRECT_MONTHS = 120): every
        // candidate period from the requested one onward is locked, so record() throws instead of
        // finding an open period, and the whole transaction — the overtime row included — rolls back.
        // A past period: OvertimeServiceImpl's own future-date check would otherwise refuse the
        // entry before the ledger is ever reached — the failure this test wants comes from the
        // ledger, not from validation.
        YearMonth period = YearMonth.of(2020, 1);
        for (int i = 0; i <= 120; i++) {
            payInputService.lock(period.plusMonths(i));
        }

        assertThatThrownBy(() -> overtimeService.record(
                        new OvertimeEntry(employee, period.atDay(5), new BigDecimal("1.00"), null, null)))
                .isInstanceOf(IllegalStateException.class);

        long overtimeRows = OvertimeTestSchema.count(
                "SELECT count(*) FROM core.overtime_request WHERE tenant_id = ? AND employee_id = ?", tenant, employee);
        assertThat(overtimeRows).isZero();
    }

    @Test
    @DisplayName("Two cancels at the same moment: one reversal row, the entry CANCELLED once, hours net to zero")
    void concurrentCancelsReverseTheLedgerOnce() throws Exception {
        OvertimeResponse recorded = overtimeService.record(
                new OvertimeEntry(employee, LocalDate.of(2026, 4, 12), new BigDecimal("8.00"), null, "double-click"));

        // A double-click: both requests read APPROVED before either commits. Only the ledger's
        // one-reversal index (W-19, uk_pay_input_tenant_reverses) can decide which one wins.
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Throwable> attempt = () -> {
            TenantContext.set(tenant);
            try {
                barrier.await(10, TimeUnit.SECONDS);
                overtimeService.cancel(recorded.id());
                return null;
            } catch (Throwable t) {
                return t;
            } finally {
                TenantContext.clear();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Throwable first;
        Throwable second;
        try {
            var f1 = pool.submit(attempt);
            var f2 = pool.submit(attempt);
            first = f1.get(30, TimeUnit.SECONDS);
            second = f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // Exactly one succeeded; the other was refused as a second reversal or a second cancel.
        assertThat(first == null ^ second == null).isTrue();
        Throwable refused = first != null ? first : second;
        assertThat(refused)
                .isInstanceOfAny(
                        com.infinevo.core.payinput.PayInputService.AlreadyReversedException.class,
                        OvertimeService.AlreadyCancelledException.class);

        var ledger = payInputService.forEmployee(employee, YearMonth.of(2026, 4));
        assertThat(ledger.rows()).hasSize(2);
        assertThat(ledger.quantityTotalsByKind().get(com.infinevo.core.payinput.PayInputKind.OVERTIME))
                .isEqualByComparingTo("0.00");
        long cancelled = OvertimeTestSchema.count(
                "SELECT count(*) FROM core.overtime_request WHERE tenant_id = ? AND id = ? AND status = 'CANCELLED'",
                tenant,
                recorded.id());
        assertThat(cancelled).isEqualTo(1);
    }
}
