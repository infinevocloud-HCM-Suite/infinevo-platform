package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating a project's status (W-41).
 */
public record ProjectStatusRequest(
        @NotNull(message = "status is required") @JsonProperty("status") ProjectStatus status) {}
