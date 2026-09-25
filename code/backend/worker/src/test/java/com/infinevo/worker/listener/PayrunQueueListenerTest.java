package com.infinevo.worker.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class PayrunQueueListenerTest {

    private JobService jobService;
    private PayrunQueueListener listener;

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        listener = new PayrunQueueListener(jobService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldProcessMessageSuccessfully() {
        UUID tenantId = UUID.randomUUID();
        String jobId = "job-456";
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "payrun", "{\"run\":\"monthly\"}");
        when(jobService.claimForRun(jobId)).thenReturn(true);

        listener.onMessage(message);

        verify(jobService).claimForRun(jobId);
        verify(jobService).updateProgress(jobId, 25);
        verify(jobService).updateProgress(jobId, 75);
        verify(jobService).markCompleted(eq(jobId), anyString());
    }

    @Test
    void shouldDropDuplicateMessageIfAlreadyCompleted() {
        UUID tenantId = UUID.randomUUID();
        String jobId = "job-456";
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "payrun", "{\"run\":\"monthly\"}");
        // A COMPLETED job is not QUEUED, so the atomic claim changes no row.
        when(jobService.claimForRun(jobId)).thenReturn(false);

        listener.onMessage(message);

        verify(jobService, never()).markRunning(jobId);
        verify(jobService, never()).updateProgress(eq(jobId), anyInt());
        verify(jobService, never()).markCompleted(eq(jobId), anyString());
    }

    @Test
    void shouldDropDuplicateMessageIfAlreadyRunning() {
        UUID tenantId = UUID.randomUUID();
        String jobId = "job-running";
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "payrun", "{\"run\":\"monthly\"}");
        // Another replica already moved it QUEUED -> RUNNING; this delivery loses the claim.
        when(jobService.claimForRun(jobId)).thenReturn(false);

        listener.onMessage(message);

        verify(jobService, never()).markRunning(jobId);
        verify(jobService, never()).updateProgress(eq(jobId), anyInt());
        verify(jobService, never()).markCompleted(eq(jobId), anyString());
    }

    @Test
    void shouldReleaseForRetryAndRethrowWhenExceptionOccurs() {
        UUID tenantId = UUID.randomUUID();
        String jobId = "job-fail";
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "payrun", "{\"run\":\"monthly\"}");
        when(jobService.claimForRun(jobId)).thenReturn(true);

        PayrunQueueListener failingListener = new PayrunQueueListener(jobService) {
            @Override
            protected void processPayrunPayload(String payload) {
                throw new RuntimeException("Calculation error");
            }
        };

        // Retry-then-fail: the job goes back to QUEUED and the exception reaches the loop, which
        // leaves the message on the queue. The loop marks FAILED on the third delivery.
        assertThrows(PayrunQueueListener.PayrunProcessingException.class, () -> failingListener.onMessage(message));

        verify(jobService).releaseForRetry(jobId, "Calculation error");
        verify(jobService, never()).markFailed(eq(jobId), anyString());
        assertNull(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
        assertNull(MDC.get(MdcLoggingContext.TENANT_ID_KEY));
        assertNull(TenantContext.current().orElse(null));
    }

    @Test
    void shouldPropagateCorrelationIdAndTenantIdToMdcDuringProcessing() {
        UUID tenantId = UUID.randomUUID();
        String jobId = "job-corr-test";
        String expectedCorrId = "corr-worker-999";
        QueueMessage<String> message =
                QueueMessage.of(jobId, tenantId, "payrun", expectedCorrId, "{\"run\":\"monthly\"}");
        when(jobService.claimForRun(jobId)).thenReturn(true);

        AtomicReference<String> mdcCorrelationId = new AtomicReference<>();
        AtomicReference<String> mdcTenantId = new AtomicReference<>();
        AtomicReference<UUID> boundTenant = new AtomicReference<>();

        PayrunQueueListener trackingListener = new PayrunQueueListener(jobService) {
            @Override
            protected void processPayrunPayload(String payload) {
                mdcCorrelationId.set(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
                mdcTenantId.set(MDC.get(MdcLoggingContext.TENANT_ID_KEY));
                boundTenant.set(TenantContext.current().orElse(null));
            }
        };

        trackingListener.onMessage(message);

        assertEquals(expectedCorrId, mdcCorrelationId.get());
        assertEquals(tenantId.toString(), mdcTenantId.get());
        assertEquals(tenantId, boundTenant.get());
        assertNull(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
        assertNull(MDC.get(MdcLoggingContext.TENANT_ID_KEY));
    }
}
