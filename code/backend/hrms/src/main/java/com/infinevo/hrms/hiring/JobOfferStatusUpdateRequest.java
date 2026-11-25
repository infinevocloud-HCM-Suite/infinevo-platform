package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload to update job offer status (W-112).
 */
public record JobOfferStatusUpdateRequest(
        @NotNull(message = "status is required") JobOfferStatus status, String reason) {}
