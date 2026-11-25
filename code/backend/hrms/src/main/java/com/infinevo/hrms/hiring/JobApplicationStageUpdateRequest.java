package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload to update job application stage (W-112).
 */
public record JobApplicationStageUpdateRequest(
        @NotNull(message = "stage is required") JobApplicationStage stage, String rejectionReason) {}
