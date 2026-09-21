package com.infinevo.core.job.service;

import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.entity.JobStatus;
import java.util.Optional;
import java.util.UUID;

public interface JobService {

    JobStatus createJob(String jobId, UUID tenantId, String queueName, String initialPayload);

    void markRunning(String jobId);

    void updateProgress(String jobId, int progressPercentage);

    void markCompleted(String jobId, String resultPayload);

    void markFailed(String jobId, String errorMessage);

    Optional<JobStatusResponseDTO> getJobStatus(String jobId, UUID tenantId);
}
