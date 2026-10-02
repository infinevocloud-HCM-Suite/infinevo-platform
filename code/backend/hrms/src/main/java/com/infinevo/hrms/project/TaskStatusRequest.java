package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating a task's status (W-41).
 */
public record TaskStatusRequest(@NotNull(message = "status is required") @JsonProperty("status") TaskStatus status) {}
