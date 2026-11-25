package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.payinput.PayInputLockStage;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * D-144: what a pay run's lock, approve and cancel do to its month's pay input lock. Lock writes
 * LOCKED (as before), approve writes CLOSED, cancel removes a LOCKED lock, and (D-151) cancelling an
 * approved run reopens its CLOSED month. A paid run cannot be cancelled.
 * An off-cycle run never touches the month's lock.
 */
class PayRunPeriodLockTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final String OFFICER = "officer";

    private PayRunRepository payRuns;
    private PayInputService payInputService;
    private PayRunServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        payRuns = mock(PayRunRepository.class);
        payInputService = mock(PayInputService.class);
        service = new PayRunServiceImpl(
                payRuns,
                mock(EmployeePayRunRepository.class),
                mock(PayPeriodService.class),
                mock(EmployeeService.class),
                mock(PayRunInclusionService.class),
                payInputService,
                mock(EmployeePayRunLineRepository.class),
                mock(JobService.class),
                (ObjectProvider<QueueProducer>) mock(ObjectProvider.class),
                null,
                null,
                null,
                Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
        when(payRuns.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PayRun stored(PayRun run) {
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        when(payRuns.findForUpdate(run.getId(), TENANT)).thenReturn(Optional.of(run));
        return run;
    }

    private static PayRun regular() {
        LocalDate start = OCTOBER.atDay(1);
        LocalDate end = OCTOBER.atEndOfMonth();
        return new PayRun(TENANT, OCTOBER, start, end, start.plusDays(24), end, 0, 0, OFFICER);
    }

    private static PayRun offCycle() {
        LocalDate start = OCTOBER.atDay(1);
        LocalDate end = OCTOBER.atEndOfMonth();
        return PayRun.ofType(
                PayRunType.OFF_CYCLE, TENANT, OCTOBER, start, end, start.plusDays(24), end, null, 0, 0, OFFICER);
    }

    private static PayRun computed(PayRun run) {
        run.lock(OFFICER, Instant.now());
        run.startComputing(OFFICER);
        run.completeComputation(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), 0, OFFICER, Instant.now());
        return run;
    }

    @Test
    @DisplayName("lock: a regular run locks its month at LOCKED, as before")
    void lockWritesLocked() {
        PayRun run = stored(regular());

        service.lock(run.getId());

        verify(payInputService).lock(OCTOBER);
        verify(payInputService, never()).lock(OCTOBER, PayInputLockStage.CLOSED);
    }

    @Test
    @DisplayName("approve: a regular run closes its month")
    void approveWritesClosed() {
        PayRun run = stored(computed(regular()));

        PayRunResponse approved = service.approve(run.getId());

        assertThat(approved.status()).isEqualTo(PayRunStatus.APPROVED);
        verify(payInputService).lock(OCTOBER, PayInputLockStage.CLOSED);
    }

    @Test
    @DisplayName("approve: refused with 409 while the month took entries after the compute; nothing is closed")
    void approveRefusedWhileInputsChanged() {
        PayRun run = stored(computed(regular()));
        when(payInputService.countChangesSince(eq(OCTOBER), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.approve(run.getId()))
                .isInstanceOf(PayRunInputsChangedException.class)
                .hasMessage("3 new pay inputs since this run was computed. Compute again before approving.");
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.COMPUTED);
        verify(payInputService, never()).lock(any(), any());
    }

    @Test
    @DisplayName("approve: the count is measured from the run's compute time")
    void approveCountsFromTheComputeTime() {
        PayRun run = stored(computed(regular()));

        service.approve(run.getId());

        verify(payInputService).countChangesSince(OCTOBER, run.getComputedAt());
    }

    @Test
    @DisplayName("approve: a wrong status keeps its own 409 and never counts")
    void approveWrongStatusNeverCounts() {
        PayRun run = stored(regular());

        assertThatThrownBy(() -> service.approve(run.getId())).isInstanceOf(IllegalPayRunTransitionException.class);
        verify(payInputService, never()).countChangesSince(any(), any());
    }

    @Test
    @DisplayName("get: the run detail carries inputs_since_compute")
    void getCarriesTheCount() {
        PayRun run = computed(regular());
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        when(payRuns.findByIdAndTenantId(run.getId(), TENANT)).thenReturn(Optional.of(run));
        when(payInputService.countChangesSince(eq(OCTOBER), any())).thenReturn(2L);

        assertThat(service.get(run.getId()).inputsSinceCompute()).isEqualTo(2L);
    }

    @Test
    @DisplayName("get: a run never computed, and an off-cycle run, carry no count and never ask core")
    void getZeroWithoutAsking() {
        PayRun draft = regular();
        ReflectionTestUtils.setField(draft, "id", UUID.randomUUID());
        when(payRuns.findByIdAndTenantId(draft.getId(), TENANT)).thenReturn(Optional.of(draft));
        PayRun offCycle = computed(offCycle());
        ReflectionTestUtils.setField(offCycle, "id", UUID.randomUUID());
        when(payRuns.findByIdAndTenantId(offCycle.getId(), TENANT)).thenReturn(Optional.of(offCycle));

        assertThat(service.get(draft.getId()).inputsSinceCompute()).isNull();
        assertThat(service.get(offCycle.getId()).inputsSinceCompute()).isNull();
        verify(payInputService, never()).countChangesSince(any(), any());
    }

    @Test
    @DisplayName("approve: an off-cycle run never closes the month")
    void approveOffCycleLeavesTheMonth() {
        PayRun run = stored(computed(offCycle()));

        service.approve(run.getId());

        verify(payInputService, never()).lock(any(), any());
    }

    @Test
    @DisplayName("cancel: a locked regular run removes the month's LOCKED lock (D-138)")
    void cancelLockedRemovesTheLock() {
        PayRun run = regular();
        run.lock(OFFICER, Instant.now());
        stored(run);
        when(payInputService.periodStage(OCTOBER)).thenReturn(Optional.of(PayInputLockStage.LOCKED));

        PayRunResponse cancelled = service.cancel(run.getId());

        assertThat(cancelled.status()).isEqualTo(PayRunStatus.CANCELLED);
        verify(payInputService).unlock(OCTOBER);
    }

    @Test
    @DisplayName(
            "D-151: cancel of an approved run reopens its CLOSED month; unlock (which refuses CLOSED) is never called")
    void cancelApprovedReopensTheMonth() {
        PayRun run = computed(regular());
        run.approve(OFFICER, Instant.now());
        stored(run);
        when(payInputService.periodStage(OCTOBER)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        PayRunResponse cancelled = service.cancel(run.getId());

        assertThat(cancelled.status()).isEqualTo(PayRunStatus.CANCELLED);
        verify(payInputService).reopen(OCTOBER);
        verify(payInputService, never()).unlock(any());
    }

    @Test
    @DisplayName("D-151: a CLOSED month is reopened only by cancelling its APPROVED run, not a locked one")
    void cancelLockedRunNeverReopensAClosedMonth() {
        PayRun run = regular();
        run.lock(OFFICER, Instant.now());
        stored(run);
        when(payInputService.periodStage(OCTOBER)).thenReturn(Optional.of(PayInputLockStage.CLOSED));

        service.cancel(run.getId());

        verify(payInputService, never()).reopen(any());
        verify(payInputService, never()).unlock(any());
    }

    @Test
    @DisplayName("cancel: a draft run with no lock never calls unlock")
    void cancelDraftWithNoLock() {
        PayRun run = stored(regular());
        when(payInputService.periodStage(OCTOBER)).thenReturn(Optional.empty());

        service.cancel(run.getId());

        verify(payInputService, never()).unlock(any());
    }

    @Test
    @DisplayName("cancel: an off-cycle run never touches the month's lock")
    void cancelOffCycleLeavesTheMonth() {
        PayRun run = offCycle();
        run.lock(OFFICER, Instant.now());
        stored(run);

        service.cancel(run.getId());

        verify(payInputService, never()).unlock(any());
    }

    @Test
    @DisplayName("cancel: a run that cannot be cancelled is refused before the lock is touched")
    void cancelRefusedLeavesTheLock() {
        PayRun run = computed(regular());
        run.approve(OFFICER, Instant.now());
        run.pay(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 23), OFFICER, Instant.now());
        stored(run);

        assertThatThrownBy(() -> service.cancel(run.getId())).isInstanceOf(IllegalPayRunTransitionException.class);
        verify(payInputService, never()).unlock(any());
        verify(payInputService, never()).reopen(any());
    }
}
