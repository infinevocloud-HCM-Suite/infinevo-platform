package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the late-input rule (W-19 §7, {@code 12-core-contracts.md} §6 decision 3) as D-144
 * narrows it: only a CLOSED month moves a write on to the next month that is not CLOSED, and the
 * response says so. A LOCKED month (run locked or computed) takes the write itself.
 */
class PayInputLatePeriodTest {

    private static final UUID TENANT = UUID.randomUUID();

    private PayInputRepository payInputs;
    private PayInputPeriodLockRepository locks;
    private PayInputServiceImpl service;

    @BeforeEach
    void setUp() {
        payInputs = mock(PayInputRepository.class);
        locks = mock(PayInputPeriodLockRepository.class);
        service = new PayInputServiceImpl(payInputs, locks);
        TenantContext.set(TENANT);
        when(locks.findPeriodStage(any(), any())).thenReturn(Optional.empty());
        when(payInputs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("record for a CLOSED period posts to the next open period and the response says so")
    void recordForAClosedPeriodIsRedirected() {
        YearMonth requested = YearMonth.of(2026, 4);
        YearMonth nextOpen = YearMonth.of(2026, 5);
        when(locks.findPeriodStage(TENANT, requested)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        PayInputResponse response = service.record(new PayInputCommand(
                UUID.randomUUID(), requested, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:late-1"));

        assertThat(response.postedPeriod()).isEqualTo(nextOpen);
        verify(payInputs).saveAndFlush(argThatPeriodIs(nextOpen));
    }

    @Test
    @DisplayName("record for a LOCKED period posts to that period - no move (D-144)")
    void recordForALockedPeriodStaysThere() {
        YearMonth requested = YearMonth.of(2026, 4);
        when(locks.findPeriodStage(TENANT, requested)).thenReturn(Optional.of(PayInputLockStage.LOCKED));

        PayInputResponse response = service.record(new PayInputCommand(
                UUID.randomUUID(), requested, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:locked"));

        assertThat(response.postedPeriod()).isEqualTo(requested);
        verify(payInputs).saveAndFlush(argThatPeriodIs(requested));
    }

    @Test
    @DisplayName("Two consecutive CLOSED periods are both skipped; a LOCKED one after them is not")
    void twoConsecutiveClosedPeriodsAreBothSkipped() {
        YearMonth requested = YearMonth.of(2026, 4);
        YearMonth firstNext = YearMonth.of(2026, 5);
        YearMonth secondNext = YearMonth.of(2026, 6);
        when(locks.findPeriodStage(TENANT, requested)).thenReturn(Optional.of(PayInputLockStage.CLOSED));
        when(locks.findPeriodStage(TENANT, firstNext)).thenReturn(Optional.of(PayInputLockStage.CLOSED));
        when(locks.findPeriodStage(TENANT, secondNext)).thenReturn(Optional.of(PayInputLockStage.LOCKED));

        PayInputResponse response = service.record(new PayInputCommand(
                UUID.randomUUID(), requested, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:late-2"));

        assertThat(response.postedPeriod()).isEqualTo(secondNext);
    }

    @Test
    @DisplayName("An open period is never redirected")
    void anOpenPeriodIsNotRedirected() {
        YearMonth period = YearMonth.of(2026, 4);

        PayInputResponse response = service.record(new PayInputCommand(
                UUID.randomUUID(), period, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:open"));

        assertThat(response.postedPeriod()).isEqualTo(period);
    }

    @Test
    @DisplayName("A reversal of a row in a CLOSED period is redirected exactly like any other late input")
    void reversalOfAClosedPeriodIsRedirected() {
        YearMonth requested = YearMonth.of(2026, 4);
        YearMonth nextOpen = YearMonth.of(2026, 5);
        UUID originalId = UUID.randomUUID();
        PayInput original = new PayInput(
                TENANT,
                UUID.randomUUID(),
                requested,
                PayInputKind.AD_HOC_DEDUCTION,
                null,
                new BigDecimal("50.00"),
                "payroll",
                "ded:late",
                null,
                "admin");
        when(payInputs.findByIdAndTenantId(eq(originalId), eq(TENANT))).thenReturn(Optional.of(original));
        when(locks.findPeriodStage(TENANT, requested)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        PayInputResponse reversal = service.reverse(originalId, "correction after close");

        assertThat(reversal.postedPeriod()).isEqualTo(nextOpen);
    }

    private static PayInput argThatPeriodIs(YearMonth period) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null && period.equals(p.getPeriod()));
    }
}
