package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * W-29.4 §7 — {@code compute} queues instead of computing: one job, one message sent after the commit,
 * the run {@code COMPUTING}; a second press within the stale window is refused; one after it starts
 * attempt 2 and carries the finished rows; a send that fails leaves the run computable again.
 */
class PayRunEnqueueTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RUN = UUID.randomUUID();

    private PayRunRepository payRuns;
    private EmployeePayRunRepository employeePayRuns;
    private JobService jobService;
    private QueueProducer producer;
    private ObjectProvider<QueueProducer> producers;
    private PlatformTransactionManager transactionManager;
    private PayRunServiceImpl service;
    private PayRun run;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        payRuns = mock(PayRunRepository.class);
        employeePayRuns = mock(EmployeePayRunRepository.class);
        jobService = mock(JobService.class);
        producer = mock(QueueProducer.class);
        producers = mock(ObjectProvider.class);
        when(producers.getIfAvailable()).thenReturn(producer);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new PayRunServiceImpl(
                payRuns,
                employeePayRuns,
                mock(PayPeriodService.class),
                mock(EmployeeService.class),
                mock(PayRunInclusionService.class),
                mock(PayInputService.class),
                mock(EmployeePayRunLineRepository.class),
                jobService,
                producers,
                transactionManager);

        run = new PayRun(
                TENANT,
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 31),
                5,
                0,
                "officer");
        run.lock("officer", Instant.now());
        when(payRuns.findForUpdate(RUN, TENANT)).thenReturn(Optional.of(run));
        when(payRuns.findByIdAndTenantId(RUN, TENANT)).thenReturn(Optional.of(run));
        when(payRuns.saveAndFlush(any(PayRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("From LOCKED: COMPUTING at attempt 1, one job, one message sent after the commit")
    void computeFromLockedQueuesOnce() {
        ComputeAcceptedResponse accepted = service.compute(RUN);

        String jobId = "payrun-" + RUN + "-1";
        assertThat(accepted).isEqualTo(new ComputeAcceptedResponse(jobId, PayRunStatus.COMPUTING, 1));
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.COMPUTING);
        assertThat(run.getComputeAttempt()).isEqualTo(1);
        assertThat(run.getJobId()).isEqualTo(jobId);
        assertThat(run.getProgressDone()).isZero();
        assertThat(run.getProgressTotal()).isEqualTo(5);
        assertThat(run.getComputeStartedAt()).isNotNull();

        ArgumentCaptor<QueueMessage<String>> sent = messageCaptor();
        InOrder order = inOrder(jobService, transactionManager, producer);
        order.verify(jobService).createJob(eq(jobId), eq(TENANT), eq("payrun"), anyString());
        order.verify(transactionManager).commit(any(TransactionStatus.class));
        order.verify(producer).send(eq("payrun"), sent.capture());
        QueueMessage<String> message = sent.getValue();
        assertThat(message.getJobId()).isEqualTo(jobId);
        assertThat(message.getTenantId()).isEqualTo(TENANT);
        assertThat(PayRunJobPayload.fromJson(message.getPayload()))
                .isEqualTo(new PayRunJobPayload(RUN, 1, PayRunServiceImpl.ACTOR_SYSTEM));
        verify(employeePayRuns, never()).carryForward(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("A second press while the job is fresh is refused: no job, no message")
    void secondPressWithinTheWindowIsRefused() {
        service.compute(RUN);
        when(jobService.getJobStatus("payrun-" + RUN + "-1", TENANT))
                .thenReturn(Optional.of(job(Instant.now().minus(Duration.ofMinutes(1)))));

        assertThatThrownBy(() -> service.compute(RUN)).isInstanceOf(PayRunComputeInProgressException.class);

        verify(jobService).createJob(anyString(), any(), anyString(), anyString());
        verify(producer).send(anyString(), any());
        assertThat(run.getComputeAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("A press 16 minutes after the last progress starts attempt 2, carries the finished rows, sends again")
    void staleRunStartsTheNextAttempt() {
        service.compute(RUN);
        when(jobService.getJobStatus("payrun-" + RUN + "-1", TENANT))
                .thenReturn(Optional.of(job(Instant.now().minus(Duration.ofMinutes(16)))));
        when(employeePayRuns.carryForward(TENANT, RUN, 1, 2)).thenReturn(3);

        ComputeAcceptedResponse accepted = service.compute(RUN);

        assertThat(accepted.computeAttempt()).isEqualTo(2);
        assertThat(accepted.jobId()).isEqualTo("payrun-" + RUN + "-2");
        assertThat(run.getStatus()).isEqualTo(PayRunStatus.COMPUTING);
        assertThat(run.getProgressDone()).isEqualTo(3);
        verify(employeePayRuns).carryForward(TENANT, RUN, 1, 2);
        verify(jobService).createJob(eq("payrun-" + RUN + "-2"), eq(TENANT), eq("payrun"), anyString());
        ArgumentCaptor<QueueMessage<String>> sent = messageCaptor();
        verify(producer, org.mockito.Mockito.times(2)).send(eq("payrun"), sent.capture());
        assertThat(PayRunJobPayload.fromJson(sent.getValue().getPayload()).attempt())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("A job FAILED after its last delivery frees the run at once: no stale wait, attempt 2")
    void failedJobFreesTheRunAtOnce() {
        service.compute(RUN);
        when(jobService.getJobStatus("payrun-" + RUN + "-1", TENANT))
                .thenReturn(Optional.of(new JobStatusResponseDTO(
                        "payrun-" + RUN + "-1", "payrun", JobState.FAILED, 40, "boom", Instant.now(), Instant.now())));

        ComputeAcceptedResponse accepted = service.compute(RUN);

        assertThat(accepted.computeAttempt()).isEqualTo(2);
        verify(employeePayRuns).carryForward(TENANT, RUN, 1, 2);
    }

    @Test
    @DisplayName("A COMPUTED run computes again from scratch: attempt 2, nothing carried")
    void recomputeOfAComputedRunCarriesNothing() {
        service.compute(RUN);
        run.completeComputation(
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                0,
                "system",
                Instant.now());

        ComputeAcceptedResponse accepted = service.compute(RUN);

        assertThat(accepted.computeAttempt()).isEqualTo(2);
        assertThat(run.getProgressDone()).isZero();
        verify(employeePayRuns, never()).carryForward(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("A send that fails: the run is FAILED with the reason, the job FAILED, the caller told")
    void producerFailureLeavesTheRunComputableAgain() {
        org.mockito.Mockito.doThrow(new IllegalStateException("queue unreachable"))
                .when(producer)
                .send(anyString(), any());

        assertThatThrownBy(() -> service.compute(RUN))
                .isInstanceOf(PayRunEnqueueException.class)
                .hasMessageContaining("queue unreachable");

        assertThat(run.getStatus()).isEqualTo(PayRunStatus.FAILED);
        assertThat(run.getFailureReason()).contains("queue unreachable");
        assertThat(PayRunStatus.FAILED.canTransitionTo(PayRunStatus.COMPUTING)).isTrue();
        verify(jobService).markFailed(eq("payrun-" + RUN + "-1"), anyString());
    }

    @Test
    @DisplayName("No queue configured: refused before anything is written")
    void noQueueIsRefusedAndRolledBack() {
        when(producers.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.compute(RUN)).isInstanceOf(PayRunEnqueueException.class);

        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
        verify(jobService, never()).createJob(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Compute of a DRAFT run is refused before the queue is looked at")
    void draftIsRefused() {
        PayRun draft = new PayRun(
                TENANT,
                YearMonth.of(2026, 8),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 31),
                1,
                0,
                "officer");
        UUID draftId = UUID.randomUUID();
        when(payRuns.findForUpdate(draftId, TENANT)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.compute(draftId)).isInstanceOf(IllegalPayRunTransitionException.class);

        verify(producer, never()).send(anyString(), any());
    }

    private static JobStatusResponseDTO job(Instant updatedAt) {
        return new JobStatusResponseDTO(
                "payrun-" + RUN + "-1", "payrun", JobState.RUNNING, 40, null, updatedAt, updatedAt);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<QueueMessage<String>> messageCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(QueueMessage.class);
    }
}
