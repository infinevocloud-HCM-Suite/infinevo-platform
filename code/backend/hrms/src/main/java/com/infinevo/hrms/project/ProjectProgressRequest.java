package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating a project's progress percentage (W-41).
 */
public record ProjectProgressRequest(
        @NotNull(message = "progress is required")
                @Min(value = 0, message = "progress must be between 0 and 100")
                @Max(value = 100, message = "progress must be between 0 and 100")
                @JsonProperty("progress")
                Integer progress) {}
