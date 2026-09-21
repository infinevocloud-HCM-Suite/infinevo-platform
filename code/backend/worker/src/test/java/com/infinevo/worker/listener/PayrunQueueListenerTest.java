package com.infinevo.worker.listener;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.queue.QueueMessage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayrunQueueListenerTest {

    private JobService jobService;
    private PayrunQueueListener listener;

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        listener = new PayrunQueueListener(jobService);
    }

    @Test
    void shouldProcessMessageSuccessfully() {
        UUID tenantId = UUID.randomUUID();
        String jobId = "job-456";
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "payrun", "{\"run\":\"monthly\"}");

        when(jobService.getJobStatus(jobId, tenantId))
                .thenReturn(Optional.of(new JobStatusResponseDTO(
                        jobId, "payrun", JobState.QUEUED, 0, null, Instant.now(), Instant.now())));

        listener.onMessage(message);

        verify(jobService).markRunning(jobId);
        verify(jobService).updateProgress(jobId, 25);
        verify(jobService).updateProgress(jobId, 75);
        verify(jobService).markCompleted(eq(jobId), anyString());
    }

    @Test
    void shouldDropDuplicateMessageIfAlreadyCompleted() {
        UUID tenantId = UUID.randomUUID();
        String jobId = "job-456";
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "payrun", "{\"run\":\"monthly\"}");

        when(jobService.getJobStatus(jobId, tenantId))
                .thenReturn(Optional.of(new JobStatusResponseDTO(
                        jobId, "payrun", JobState.COMPLETED, 100, null, Instant.now(), Instant.now())));

        listener.onMessage(message);

        verify(jobService, never()).markRunning(jobId);
        verify(jobService, never()).updateProgress(eq(jobId), anyInt());
        verify(jobService, never()).markCompleted(eq(jobId), anyString());
    }

    @Test
    void shouldMarkFailedWhenExceptionOccurs() {
        UUID tenantId = UUID.randomUUID();
        String jobId = "job-fail";
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "payrun", "{\"run\":\"monthly\"}");

        PayrunQueueListener failingListener = new PayrunQueueListener(jobService) {
            @Override
            protected void processPayrunPayload(String payload) {
                throw new RuntimeException("Calculation error");
            }
        };

        failingListener.onMessage(message);

        verify(jobService).markFailed(jobId, "Calculation error");
    }
}
