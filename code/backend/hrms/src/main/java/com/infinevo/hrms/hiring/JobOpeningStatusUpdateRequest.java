package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload to transition job opening status (W-112).
 */
public record JobOpeningStatusUpdateRequest(@NotNull(message = "status is required") JobOpeningStatus status) {}
