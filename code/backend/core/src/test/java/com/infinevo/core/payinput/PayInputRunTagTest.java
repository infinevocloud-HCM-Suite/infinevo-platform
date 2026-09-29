package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the run tag (W-30.1 §7): a tagged row obeys its run's lock, not the period's, and
 * is invisible to {@code forPeriod}. The database trigger that backs the lock is
 * {@code PayInputRunLockIT}'s; this class is the service branch that avoids ever reaching it in
 * the normal, Java-mediated path.
 */
class PayInputRunTagTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final YearMonth PERIOD = YearMonth.of(2026, 4);

    private PayInputRepository payInputs;
    private PayInputPeriodLockRepository locks;
    private PayInputServiceImpl service;

    @BeforeEach
    void setUp() {
        payInputs = mock(PayInputRepository.class);
        locks = mock(PayInputPeriodLockRepository.class);
        service = new PayInputServiceImpl(payInputs, locks);
        TenantContext.set(TENANT);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(any(), any())).thenReturn(false);
        when(locks.existsByTenantIdAndRunRef(any(), any())).thenReturn(false);
        when(payInputs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("record with runRef on a locked run throws RunLockedException, not a redirect")
    void recordOnALockedRunIsRefusedOutright() {
        UUID runRef = UUID.randomUUID();
        when(locks.existsByTenantIdAndRunRef(TENANT, runRef)).thenReturn(true);

        assertThatThrownBy(() -> service.record(new PayInputCommand(
                        UUID.randomUUID(),
                        PERIOD,
                        PayInputKind.ONE_TIME_PAYOUT,
                        null,
                        com.infinevo.shared.money.Money.of("500"),
                        "payroll",
                        "bonus:1",
                        runRef)))
                .isInstanceOf(PayInputService.RunLockedException.class);

        verify(payInputs, never()).saveAndFlush(any());
        // The period lock is never consulted for a tagged row (spec §3): no redirect search happens.
        verify(locks, never()).existsByTenantIdAndPeriodAndRunRefIsNull(any(), any());
    }

    @Test
    @DisplayName("record with runRef on a locked PERIOD still succeeds: the run's own lock applies, not the period's")
    void recordOnALockedPeriodStillSucceedsWhenTagged() {
        UUID runRef = UUID.randomUUID();
        // The whole period is locked...
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, PERIOD)).thenReturn(true);
        // ...but the run itself is not.
        when(locks.existsByTenantIdAndRunRef(TENANT, runRef)).thenReturn(false);

        PayInputResponse response = service.record(new PayInputCommand(
                UUID.randomUUID(),
                PERIOD,
                PayInputKind.ONE_TIME_PAYOUT,
                null,
                com.infinevo.shared.money.Money.of("500"),
                "payroll",
                "bonus:2",
                runRef));

        assertThat(response.postedPeriod()).isEqualTo(PERIOD);
        assertThat(response.runRef()).isEqualTo(runRef);
    }

    @Test
    @DisplayName("forPeriod totals exclude tagged rows: only the untagged rows the repository returns are summed")
    void forPeriodTotalsExcludeTaggedRows() {
        UUID employeeId = UUID.randomUUID();
        // findByTenantIdAndPeriodAndRunRefIsNull is the untagged-only read; a mock that returns only
        // the untagged row proves forPeriod asks for exactly that, not every row for the period.
        when(payInputs.findByTenantIdAndPeriodAndRunRefIsNull(TENANT, PERIOD))
                .thenReturn(List.of(new PayInput(
                        TENANT,
                        employeeId,
                        PERIOD,
                        PayInputKind.LOP_DAYS,
                        BigDecimal.ONE,
                        null,
                        "hrms",
                        "untagged-1",
                        null,
                        "x")));

        PayInputListResponse response = service.forPeriod(PERIOD);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.quantityTotalsByKind().get(PayInputKind.LOP_DAYS)).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("forRun reads every row tagged to that run in one statement")
    void forRunReadsTaggedRows() {
        UUID runRef = UUID.randomUUID();
        UUID employeeA = UUID.randomUUID();
        UUID employeeB = UUID.randomUUID();
        when(payInputs.findByTenantIdAndRunRef(TENANT, runRef))
                .thenReturn(List.of(
                        new PayInput(
                                TENANT,
                                employeeA,
                                PERIOD,
                                PayInputKind.ONE_TIME_PAYOUT,
                                null,
                                new BigDecimal("500.00"),
                                "payroll",
                                "bonus:a",
                                runRef,
                                null,
                                "x"),
                        new PayInput(
                                TENANT,
                                employeeB,
                                PERIOD,
                                PayInputKind.ONE_TIME_PAYOUT,
                                null,
                                new BigDecimal("750.00"),
                                "payroll",
                                "bonus:b",
                                runRef,
                                null,
                                "x")));

        PayInputRunResponse response = service.forRun(runRef);

        assertThat(response.rows()).hasSize(2);
        assertThat(response.amountTotalsByEmployeeAndKind().get(employeeA).get(PayInputKind.ONE_TIME_PAYOUT))
                .isEqualByComparingTo("500.00");
        assertThat(response.amountTotalsByEmployeeAndKind().get(employeeB).get(PayInputKind.ONE_TIME_PAYOUT))
                .isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("lockRun is idempotent: locking the same run twice leaves one row")
    void lockRunTwiceIsANoOp() {
        UUID runRef = UUID.randomUUID();
        when(locks.insertRunLockIfAbsent(eq(TENANT), eq(PERIOD.toString()), eq(runRef), any()))
                .thenReturn(1)
                .thenReturn(0);

        service.lockRun(runRef, PERIOD);
        service.lockRun(runRef, PERIOD); // no exception - a no-op
    }

    @Test
    @DisplayName("A reversal of a tagged row stays tagged to the same run, while that run is still open")
    void reversalOfATaggedRowStaysTaggedWhileTheRunIsOpen() {
        UUID runRef = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        PayInput original = new PayInput(
                TENANT,
                UUID.randomUUID(),
                PERIOD,
                PayInputKind.ONE_TIME_PAYOUT,
                null,
                new BigDecimal("500.00"),
                "payroll",
                "bonus:3",
                runRef,
                null,
                "admin");
        when(payInputs.findByIdAndTenantId(eq(originalId), eq(TENANT))).thenReturn(java.util.Optional.of(original));
        when(locks.existsByTenantIdAndRunRef(TENANT, runRef)).thenReturn(false);

        PayInputResponse reversal = service.reverse(originalId, "correction");

        assertThat(reversal.runRef()).isEqualTo(runRef);
        assertThat(reversal.postedPeriod()).isEqualTo(PERIOD);
    }

    @Test
    @DisplayName("A reversal of a tagged row whose run is now locked is not refused: it falls back to an untagged"
            + " correction, redirected the same way a locked period's reversal already is")
    void reversalOfATaggedRowFallsBackToUntaggedWhenTheRunIsLocked() {
        UUID runRef = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        PayInput original = new PayInput(
                TENANT,
                UUID.randomUUID(),
                PERIOD,
                PayInputKind.ONE_TIME_PAYOUT,
                null,
                new BigDecimal("500.00"),
                "payroll",
                "bonus:4",
                runRef,
                null,
                "admin");
        when(payInputs.findByIdAndTenantId(eq(originalId), eq(TENANT))).thenReturn(java.util.Optional.of(original));
        when(locks.existsByTenantIdAndRunRef(TENANT, runRef)).thenReturn(true);
        // The calendar period itself is open, so the untagged fallback lands in the same period.
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, PERIOD)).thenReturn(false);

        PayInputResponse reversal = service.reverse(originalId, "correction after run locked");

        assertThat(reversal.runRef()).isNull();
        assertThat(reversal.postedPeriod()).isEqualTo(PERIOD);
    }
}
