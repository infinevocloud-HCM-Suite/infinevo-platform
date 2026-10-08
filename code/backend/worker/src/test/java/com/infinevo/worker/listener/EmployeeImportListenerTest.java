package com.infinevo.worker.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employeeimport.EmployeeImportService;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-73.7: the worker claims the job, completes it with the summary, fails it without retry, drops repeats. */
class EmployeeImportListenerTest {

    private final JobService jobService = mock(JobService.class);
    private final EmployeeImportService importService = mock(EmployeeImportService.class);
    private final EmployeeImportListener listener = new EmployeeImportListener(jobService, importService);
    private final UUID tenant = UUID.randomUUID();

    @Test
    @DisplayName("listens on the provisioned import queue")
    void queueName() {
        assertThat(listener.getQueueName()).isEqualTo("import");
    }

    @Test
    @DisplayName("a claimed job runs under its tenant and completes with the summary")
    void completes() {
        when(jobService.claimForRun("j1")).thenReturn(true);
        when(importService.runJob("j1")).thenAnswer(i -> {
            assertThat(TenantContext.require()).isEqualTo(tenant);
            return "{\"totalCount\":1}";
        });

        listener.onMessage(QueueMessage.of("j1", tenant, "import", "IMPORT"));

        verify(jobService).markCompleted("j1", "{\"totalCount\":1}");
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("a job that throws is FAILED with the reason, not released for retry")
    void failsWithoutRetry() {
        when(jobService.claimForRun("j1")).thenReturn(true);
        when(importService.runJob("j1")).thenThrow(new IllegalArgumentException("Job j1 holds no import input"));

        listener.onMessage(QueueMessage.of("j1", tenant, "import", "IMPORT"));

        verify(jobService).markFailed("j1", "Job j1 holds no import input");
        verify(jobService, never()).releaseForRetry(anyString(), anyString());
    }

    @Test
    @DisplayName("a duplicate delivery loses the claim and runs nothing")
    void duplicateDropped() {
        when(jobService.claimForRun("j1")).thenReturn(false);

        listener.onMessage(QueueMessage.of("j1", tenant, "import", "IMPORT"));

        verify(importService, never()).runJob(anyString());
    }
}
