package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 * Unit test for D-144: the one posting rule behind {@code posting}, {@code record} and
 * {@code recordEntry}; the lock's stage; and removing a LOCKED lock.
 */
class PayInputPostingTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

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

    private static PayInputCommand lopFor(YearMonth period) {
        return new PayInputCommand(
                UUID.randomUUID(), period, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "core", "manual-lop:1");
    }

    @Test
    @DisplayName("posting: no lock is OPEN and posts to the month asked for")
    void postingOpen() {
        assertThat(service.posting(OCTOBER))
                .isEqualTo(new PayInputPosting(OCTOBER, OCTOBER, PayInputPostingState.OPEN));
    }

    @Test
    @DisplayName("posting: a LOCKED month is LOCKED and still posts there")
    void postingLocked() {
        when(locks.findPeriodStage(TENANT, OCTOBER)).thenReturn(Optional.of(PayInputLockStage.LOCKED));

        assertThat(service.posting(OCTOBER))
                .isEqualTo(new PayInputPosting(OCTOBER, OCTOBER, PayInputPostingState.LOCKED));
    }

    @Test
    @DisplayName("posting: a CLOSED month is CLOSED and names the first month that is not CLOSED")
    void postingClosed() {
        when(locks.findPeriodStage(TENANT, OCTOBER)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        assertThat(service.posting(OCTOBER))
                .isEqualTo(new PayInputPosting(OCTOBER, NOVEMBER, PayInputPostingState.CLOSED));
    }

    @Test
    @DisplayName("recordEntry for an OPEN month posts there")
    void recordEntryOpen() {
        assertThat(service.recordEntry(lopFor(OCTOBER)).postedPeriod()).isEqualTo(OCTOBER);
    }

    @Test
    @DisplayName("recordEntry for a LOCKED month posts there - no move, the run must be recomputed")
    void recordEntryLocked() {
        when(locks.findPeriodStage(TENANT, OCTOBER)).thenReturn(Optional.of(PayInputLockStage.LOCKED));

        assertThat(service.recordEntry(lopFor(OCTOBER)).postedPeriod()).isEqualTo(OCTOBER);
    }

    @Test
    @DisplayName("recordEntry for a CLOSED month is refused with the message naming the month to use")
    void recordEntryClosedIsRefused() {
        when(locks.findPeriodStage(TENANT, OCTOBER)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        assertThatThrownBy(() -> service.recordEntry(lopFor(OCTOBER)))
                .isInstanceOf(PayInputService.PeriodClosedException.class)
                .hasMessage("October 2026 is closed (run approved). Enter this for November 2026.")
                .satisfies(e -> {
                    PayInputService.PeriodClosedException closed = (PayInputService.PeriodClosedException) e;
                    assertThat(closed.period()).isEqualTo(OCTOBER);
                    assertThat(closed.firstOpenPeriod()).isEqualTo(NOVEMBER);
                });
        verify(payInputs, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("recordEntry names the first month after a run of CLOSED months")
    void recordEntryClosedSkipsConsecutiveClosedMonths() {
        when(locks.findPeriodStage(TENANT, OCTOBER)).thenReturn(Optional.of(PayInputLockStage.CLOSED));
        when(locks.findPeriodStage(TENANT, NOVEMBER)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        assertThatThrownBy(() -> service.recordEntry(lopFor(OCTOBER)))
                .hasMessage("October 2026 is closed (run approved). Enter this for December 2026.");
    }

    @Test
    @DisplayName("record (no person looking) for a CLOSED month still moves on, and agrees with posting")
    void recordClosedMovesOnAndAgreesWithPosting() {
        when(locks.findPeriodStage(TENANT, OCTOBER)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        YearMonth posted = service.record(lopFor(OCTOBER)).postedPeriod();

        assertThat(posted).isEqualTo(service.posting(OCTOBER).postedPeriod()).isEqualTo(NOVEMBER);
    }

    @Test
    @DisplayName("lock at CLOSED on a LOCKED month raises it")
    void lockClosedRaisesALockedMonth() {
        when(locks.insertIfAbsent(eq(TENANT), eq("2026-10"), eq("CLOSED"), anyString()))
                .thenReturn(0);
        when(locks.closeIfLocked(eq(TENANT), eq("2026-10"), anyString())).thenReturn(1);

        service.lock(OCTOBER, PayInputLockStage.CLOSED);

        verify(locks).closeIfLocked(eq(TENANT), eq("2026-10"), anyString());
    }

    @Test
    @DisplayName("lock at CLOSED with no lock yet inserts it CLOSED and never updates")
    void lockClosedInsertsClosed() {
        when(locks.insertIfAbsent(eq(TENANT), eq("2026-10"), eq("CLOSED"), anyString()))
                .thenReturn(1);

        service.lock(OCTOBER, PayInputLockStage.CLOSED);

        verify(locks, never()).closeIfLocked(any(), any(), any());
    }

    @Test
    @DisplayName("lock at LOCKED on an existing lock never touches its stage - a CLOSED month is never lowered")
    void lockLockedNeverLowers() {
        when(locks.insertIfAbsent(eq(TENANT), eq("2026-10"), eq("LOCKED"), anyString()))
                .thenReturn(0);

        service.lock(OCTOBER);

        verify(locks, never()).closeIfLocked(any(), any(), any());
    }

    @Test
    @DisplayName("unlock removes a LOCKED lock")
    void unlockLocked() {
        when(locks.findPeriodStage(TENANT, OCTOBER)).thenReturn(Optional.of(PayInputLockStage.LOCKED));
        when(locks.deleteIfLocked(TENANT, "2026-10")).thenReturn(1);

        assertThat(service.unlock(OCTOBER)).isTrue();
    }

    @Test
    @DisplayName("unlock of a CLOSED month is refused and deletes nothing")
    void unlockClosedIsRefused() {
        when(locks.findPeriodStage(TENANT, OCTOBER)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        assertThatThrownBy(() -> service.unlock(OCTOBER))
                .isInstanceOf(PayInputService.PeriodClosedException.class)
                .hasMessage("October 2026 is closed (run approved); its lock cannot be removed.");
        verify(locks, never()).deleteIfLocked(any(), any());
    }

    @Test
    @DisplayName("unlock with no lock is a no-op")
    void unlockNothing() {
        assertThat(service.unlock(OCTOBER)).isFalse();
        verify(locks, never()).deleteIfLocked(any(), any());
    }
}
