package com.infinevo.core.job.service;

import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.entity.JobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobService {

    JobStatus createJob(String jobId, UUID tenantId, String queueName, String initialPayload);

    void markRunning(String jobId);

    /**
     * Atomically takes a QUEUED job to RUNNING. Returns false when the job is missing or already
     * RUNNING, COMPLETED or FAILED - the caller must then drop the message. The single UPDATE
     * is what makes two worker replicas receiving the same job safe (W-52.1, 12-core-contracts §5).
     */
    boolean claimForRun(String jobId);

    /**
     * Puts a RUNNING job back to QUEUED after a failed attempt, keeping the error, so the next
     * delivery of the same message can claim it again. The queue loop marks it FAILED once the
     * delivery count reaches the limit (retry-then-fail, 12-core-contracts §5).
     */
    void releaseForRetry(String jobId, String errorMessage);

    void updateProgress(String jobId, int progressPercentage);

    void markCompleted(String jobId, String resultPayload);

    /**
     * Marks the job FAILED, keeping the last attempt's error. An {@code import} job's payload — the uploaded
     * file while queued — is cut to its {@code kind} (W-73.7); every other queue keeps its payload.
     */
    void markFailed(String jobId, String errorMessage);

    Optional<JobStatusResponseDTO> getJobStatus(String jobId, UUID tenantId);

    /** The tenant's twenty most recent jobs on {@code queueName}, newest first (W-73.7 import history). */
    List<JobStatusResponseDTO> recentJobs(UUID tenantId, String queueName);
}
