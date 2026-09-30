package com.infinevo.worker.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.job.service.JobService;
import com.infinevo.payroll.payrun.PayRunComputationService;
import com.infinevo.payroll.payrun.PayRunJobPayload;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.payrun.ProgressReporter;
import com.infinevo.payroll.payrun.SupersededPayRunJobException;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.MDC;

/** W-52.1's claim and retry rules, and W-29.4's call into the pay run computation (§7). */
class PayrunQueueListenerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();

    private JobService jobService;
    private PayRunComputationService computationService;
    private PayrunQueueListener listener;

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        computationService = mock(PayRunComputationService.class);
        listener = new PayrunQueueListener(jobService, computationService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void computesTheAttemptThePayloadNamesAndCompletesTheJob() {
        String jobId = "payrun-" + RUN + "-2";
        when(jobService.claimForRun(jobId)).thenReturn(true);
        when(computationService.compute(eq(RUN), eq(2), eq("officer@example.com"), any()))
                .thenReturn(run(PayRunStatus.COMPUTED, null));

        listener.onMessage(message(jobId, new PayRunJobPayload(RUN, 2, "officer@example.com").toJson()));

        InOrder order = inOrder(jobService, computationService);
        order.verify(jobService).claimForRun(jobId);
        order.verify(computationService).compute(eq(RUN), eq(2), eq("officer@example.com"), any());
        order.verify(jobService).markCompleted(eq(jobId), anyString());
        verify(jobService, never()).markFailed(anyString(), anyString());
    }

    @Test
    void progressReportsBecomeJobPercentages() {
        String jobId = "payrun-" + RUN + "-1";
        when(jobService.claimForRun(jobId)).thenReturn(true);
        when(computationService.compute(eq(RUN), eq(1), anyString(), any())).thenAnswer(invocation -> {
            ProgressReporter reporter = invocation.getArgument(3);
            reporter.report(10, 40);
            reporter.report(20, 40);
            reporter.report(40, 40);
            return run(PayRunStatus.COMPUTED, null);
        });

        listener.onMessage(message(jobId, new PayRunJobPayload(RUN, 1, "system").toJson()));

        InOrder order = inOrder(jobService);
        order.verify(jobService).updateProgress(jobId, 25);
        order.verify(jobService).updateProgress(jobId, 50);
        order.verify(jobService).updateProgress(jobId, 100);
        order.verify(jobService).markCompleted(eq(jobId), anyString());
    }

    @Test
    void aFailedRunFailsTheJobWithoutRetry() {
        String jobId = "payrun-" + RUN + "-1";
        when(jobService.claimForRun(jobId)).thenReturn(true);
        when(computationService.compute(eq(RUN), eq(1), anyString(), any()))
                .thenReturn(run(PayRunStatus.FAILED, "2 of 5 employees could not be computed"));

        listener.onMessage(message(jobId, new PayRunJobPayload(RUN, 1, "system").toJson()));

        verify(jobService).markFailed(eq(jobId), contains("2 of 5 employees could not be computed"));
        verify(jobService, never()).markCompleted(anyString(), anyString());
        verify(jobService, never()).releaseForRetry(anyString(), anyString());
    }

    @Test
    void aSupersededAttemptFailsTheJobWithoutRetry() {
        String jobId = "payrun-" + RUN + "-1";
        when(jobService.claimForRun(jobId)).thenReturn(true);
        when(computationService.compute(eq(RUN), eq(1), anyString(), any()))
                .thenThrow(new SupersededPayRunJobException(RUN, 1, PayRunStatus.COMPUTING, 2));

        listener.onMessage(message(jobId, new PayRunJobPayload(RUN, 1, "system").toJson()));

        verify(jobService).markFailed(eq(jobId), contains("superseded"));
        verify(jobService, never()).releaseForRetry(anyString(), anyString());
    }

    @Test
    void aRunOutsideTheBoundTenantFailsTheJobWithoutRetry() {
        String jobId = "payrun-" + RUN + "-1";
        when(jobService.claimForRun(jobId)).thenReturn(true);
        when(computationService.compute(eq(RUN), eq(1), anyString(), any()))
                .thenThrow(new PayRunNotFoundException(RUN));

        listener.onMessage(message(jobId, new PayRunJobPayload(RUN, 1, "system").toJson()));

        verify(jobService).markFailed(eq(jobId), contains("No pay run"));
        verify(jobService, never()).releaseForRetry(anyString(), anyString());
    }

    @Test
    void anUnreadablePayloadFailsTheJobWithoutComputing() {
        String jobId = "payrun-bad";
        when(jobService.claimForRun(jobId)).thenReturn(true);

        listener.onMessage(message(jobId, "{\"run\":\"monthly\"}"));

        verify(jobService).markFailed(eq(jobId), contains("pay run job payload"));
        verifyNoInteractions(computationService);
    }

    @Test
    void shouldDropDuplicateMessageIfAlreadyCompleted() {
        String jobId = "job-456";
        // A COMPLETED job is not QUEUED, so the atomic claim changes no row.
        when(jobService.claimForRun(jobId)).thenReturn(false);

        listener.onMessage(message(jobId, new PayRunJobPayload(RUN, 1, "system").toJson()));

        verify(jobService, never()).updateProgress(eq(jobId), anyInt());
        verify(jobService, never()).markCompleted(eq(jobId), anyString());
        verifyNoInteractions(computationService);
    }

    @Test
    void shouldDropDuplicateMessageIfAlreadyRunning() {
        String jobId = "job-running";
        // Another replica already moved it QUEUED -> RUNNING; this delivery loses the claim.
        when(jobService.claimForRun(jobId)).thenReturn(false);

        listener.onMessage(message(jobId, new PayRunJobPayload(RUN, 1, "system").toJson()));

        verify(jobService, never()).markRunning(jobId);
        verify(jobService, never()).markCompleted(eq(jobId), anyString());
        verifyNoInteractions(computationService);
    }

    @Test
    void shouldReleaseForRetryAndRethrowWhenExceptionOccurs() {
        String jobId = "job-fail";
        when(jobService.claimForRun(jobId)).thenReturn(true);
        when(computationService.compute(eq(RUN), eq(1), anyString(), any()))
                .thenThrow(new IllegalStateException("Calculation error"));

        // Retry-then-fail: the job goes back to QUEUED and the exception reaches the loop, which
        // leaves the message on the queue. The loop marks FAILED on the third delivery.
        QueueMessage<String> message = message(jobId, new PayRunJobPayload(RUN, 1, "system").toJson());
        assertThrows(PayrunQueueListener.PayrunProcessingException.class, () -> listener.onMessage(message));

        verify(jobService).releaseForRetry(jobId, "Calculation error");
        verify(jobService, never()).markFailed(eq(jobId), anyString());
        assertNull(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
        assertNull(MDC.get(MdcLoggingContext.TENANT_ID_KEY));
        assertNull(TenantContext.current().orElse(null));
    }

    @Test
    void shouldPropagateCorrelationIdAndTenantIdToMdcDuringProcessing() {
        String jobId = "job-corr-test";
        String expectedCorrId = "corr-worker-999";
        QueueMessage<String> message = QueueMessage.of(
                jobId, TENANT, "payrun", expectedCorrId, new PayRunJobPayload(RUN, 1, "system").toJson());
        when(jobService.claimForRun(jobId)).thenReturn(true);

        AtomicReference<String> mdcCorrelationId = new AtomicReference<>();
        AtomicReference<String> mdcTenantId = new AtomicReference<>();
        AtomicReference<UUID> boundTenant = new AtomicReference<>();
        when(computationService.compute(eq(RUN), eq(1), anyString(), any())).thenAnswer(invocation -> {
            mdcCorrelationId.set(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
            mdcTenantId.set(MDC.get(MdcLoggingContext.TENANT_ID_KEY));
            boundTenant.set(TenantContext.current().orElse(null));
            return run(PayRunStatus.COMPUTED, null);
        });

        listener.onMessage(message);

        assertEquals(expectedCorrId, mdcCorrelationId.get());
        assertEquals(TENANT.toString(), mdcTenantId.get());
        assertEquals(TENANT, boundTenant.get());
        assertNull(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
        assertNull(MDC.get(MdcLoggingContext.TENANT_ID_KEY));
    }

    @Test
    void percentageIsWholeAndBounded() {
        assertEquals(0, PayrunQueueListener.percentage(0, 100));
        assertEquals(33, PayrunQueueListener.percentage(1, 3));
        assertEquals(100, PayrunQueueListener.percentage(100, 100));
        assertEquals(100, PayrunQueueListener.percentage(0, 0));
    }

    private static QueueMessage<String> message(String jobId, String payload) {
        return QueueMessage.of(jobId, TENANT, "payrun", payload);
    }

    private static PayRunResponse run(PayRunStatus status, String failureReason) {
        Instant now = Instant.now();
        BigDecimal zero = BigDecimal.ZERO.setScale(4);
        return new PayRunResponse(
                RUN,
                "2026-07",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 31),
                PayRunType.REGULAR,
                status,
                5,
                0,
                zero,
                zero,
                zero,
                0,
                now,
                failureReason,
                now,
                "officer",
                null,
                null,
                "payrun-" + RUN + "-1",
                1,
                now,
                5,
                5,
                now,
                now);
    }
}
