package com.infinevo.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.entity.JobStatus;
import com.infinevo.core.job.repository.JobStatusRepository;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.job.serviceimpl.JobServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobServiceTest {

    private JobStatusRepository repository;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        repository = mock(JobStatusRepository.class);
        jobService = new JobServiceImpl(repository);
    }

    @Test
    void shouldCreateJobInQueuedState() {
        UUID tenantId = UUID.randomUUID();
        String jobId = UUID.randomUUID().toString();

        when(repository.save(any(JobStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobStatus created = jobService.createJob(jobId, tenantId, "payrun", "initial");
        assertNotNull(created);
        assertEquals(jobId, created.getJobId());
        assertEquals(tenantId, created.getTenantId());
        assertEquals("payrun", created.getQueueName());
        assertEquals(JobState.QUEUED, created.getStatus());
        assertEquals(0, created.getProgressPercentage());
        assertEquals("initial", created.getResultPayload());

        verify(repository).save(any(JobStatus.class));
    }

    @Test
    void shouldMarkRunning() {
        String jobId = "job-123";
        JobStatus job = new JobStatus(jobId, UUID.randomUUID(), "payrun", JobState.QUEUED);
        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(repository.save(any(JobStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jobService.markRunning(jobId);

        assertEquals(JobState.RUNNING, job.getStatus());
        verify(repository).save(job);
    }

    @Test
    void shouldUpdateProgress() {
        String jobId = "job-123";
        JobStatus job = new JobStatus(jobId, UUID.randomUUID(), "payrun", JobState.RUNNING);
        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        jobService.updateProgress(jobId, 45);

        assertEquals(45, job.getProgressPercentage());
        verify(repository).save(job);
    }

    @Test
    void shouldMarkCompleted() {
        String jobId = "job-123";
        JobStatus job = new JobStatus(jobId, UUID.randomUUID(), "payrun", JobState.RUNNING);
        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        jobService.markCompleted(jobId, "Processed 100 employees");

        assertEquals(JobState.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgressPercentage());
        assertEquals("Processed 100 employees", job.getResultPayload());
        verify(repository).save(job);
    }

    @Test
    void shouldMarkFailed() {
        String jobId = "job-123";
        JobStatus job = new JobStatus(jobId, UUID.randomUUID(), "payrun", JobState.RUNNING);
        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        jobService.markFailed(jobId, "Database timeout");

        assertEquals(JobState.FAILED, job.getStatus());
        assertEquals("Database timeout", job.getErrorMessage());
        verify(repository).save(job);
    }

    @Test
    void shouldGetJobStatus() {
        String jobId = "job-123";
        UUID tenantId = UUID.randomUUID();
        JobStatus job = new JobStatus(jobId, tenantId, "payrun", JobState.RUNNING);
        job.setProgressPercentage(60);

        when(repository.findByJobIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));

        Optional<JobStatusResponseDTO> dto = jobService.getJobStatus(jobId, tenantId);
        assertTrue(dto.isPresent());
        assertEquals(jobId, dto.get().jobId());
        assertEquals(JobState.RUNNING, dto.get().status());
        assertEquals(60, dto.get().progressPercentage());
    }
}
