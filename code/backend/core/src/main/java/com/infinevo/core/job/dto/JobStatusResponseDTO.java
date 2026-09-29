package com.infinevo.core.job.dto;

import com.infinevo.core.job.JobState;
import java.time.Instant;

public record JobStatusResponseDTO(
        String jobId,
        String queueName,
        JobState status,
        Integer progressPercentage,
        String resultPayload,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public JobStatusResponseDTO(
            String jobId,
            String queueName,
            JobState status,
            Integer progressPercentage,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt) {
        this(jobId, queueName, status, progressPercentage, null, errorMessage, createdAt, updatedAt);
    }
}
