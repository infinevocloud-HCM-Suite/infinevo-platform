package com.infinevo.core.job.serviceimpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employeeimport.EmployeeImportService;
import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.entity.JobStatus;
import com.infinevo.core.job.repository.JobStatusRepository;
import com.infinevo.core.job.service.JobService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobServiceImpl implements JobService {

    /**
     * Queues whose queued payload is the caller's input rather than a pointer — the bulk import holds the
     * uploaded file, names and emails included (W-73.7). A completed job's summary replaces it; a failed
     * one must not keep it either, so {@link #markFailed} cuts it to its {@code kind}. Every other queue
     * keeps its payload on failure, as before — a failed pay run's payload is what a retry reads.
     */
    static final Set<String> INPUT_CLEARED_ON_FAILURE = Set.of(EmployeeImportService.QUEUE_NAME);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JobStatusRepository jobStatusRepository;

    public JobServiceImpl(JobStatusRepository jobStatusRepository) {
        this.jobStatusRepository = Objects.requireNonNull(jobStatusRepository, "jobStatusRepository must not be null");
    }

    @Override
    @Transactional
    public JobStatus createJob(String jobId, UUID tenantId, String queueName, String initialPayload) {
        JobStatus job = new JobStatus(jobId, tenantId, queueName, JobState.QUEUED);
        job.setResultPayload(initialPayload);
        job.setProgressPercentage(0);
        return jobStatusRepository.save(job);
    }

    @Override
    @Transactional
    public void markRunning(String jobId) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() != JobState.COMPLETED && job.getStatus() != JobState.FAILED) {
                job.setStatus(JobState.RUNNING);
                jobStatusRepository.save(job);
            }
        });
    }

    @Override
    @Transactional
    public boolean claimForRun(String jobId) {
        return jobStatusRepository.transition(jobId, JobState.QUEUED, JobState.RUNNING, Instant.now()) == 1;
    }

    @Override
    @Transactional
    public void releaseForRetry(String jobId, String errorMessage) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == JobState.RUNNING) {
                job.setStatus(JobState.QUEUED);
                job.setErrorMessage(errorMessage);
                jobStatusRepository.save(job);
            }
        });
    }

    @Override
    @Transactional
    public void updateProgress(String jobId, int progressPercentage) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            job.setProgressPercentage(progressPercentage);
            jobStatusRepository.save(job);
        });
    }

    @Override
    @Transactional
    public void markCompleted(String jobId, String resultPayload) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobState.COMPLETED);
            job.setProgressPercentage(100);
            job.setResultPayload(resultPayload);
            jobStatusRepository.save(job);
        });
    }

    @Override
    @Transactional
    public void markFailed(String jobId, String errorMessage) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobState.FAILED);
            // Keep the last attempt's error next to the reason the loop gave up.
            String previous = job.getErrorMessage();
            job.setErrorMessage(
                    previous == null || previous.isBlank()
                            ? errorMessage
                            : errorMessage + " (last attempt: " + previous + ")");
            if (INPUT_CLEARED_ON_FAILURE.contains(job.getQueueName())) {
                job.setResultPayload(kindOnly(job.getResultPayload()));
            }
            jobStatusRepository.save(job);
        });
    }

    /** {@code {"kind":"…"}} from a JSON payload that names one; otherwise nothing is kept. */
    static String kindOnly(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode kind = JSON.readTree(payload).get("kind");
            if (kind == null || !kind.isTextual()) {
                return null;
            }
            return JSON.createObjectNode().put("kind", kind.asText()).toString();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobStatusResponseDTO> getJobStatus(String jobId, UUID tenantId) {
        return jobStatusRepository.findByJobIdAndTenantId(jobId, tenantId).map(JobServiceImpl::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobStatusResponseDTO> recentJobs(UUID tenantId, String queueName) {
        return jobStatusRepository.findTop20ByTenantIdAndQueueNameOrderByCreatedAtDesc(tenantId, queueName).stream()
                .map(JobServiceImpl::toResponse)
                .toList();
    }

    private static JobStatusResponseDTO toResponse(JobStatus job) {
        return new JobStatusResponseDTO(
                job.getJobId(),
                job.getQueueName(),
                job.getStatus(),
                job.getProgressPercentage(),
                job.getResultPayload(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
