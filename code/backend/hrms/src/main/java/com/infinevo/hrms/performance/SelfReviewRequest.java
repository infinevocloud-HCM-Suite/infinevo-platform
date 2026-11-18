package com.infinevo.hrms.performance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for employee self-review submission (W-106.1).
 */
public record SelfReviewRequest(
        @NotNull(message = "selfRating is required")
                @Min(value = 1, message = "Rating must be at least 1")
                @Max(value = 5, message = "Rating must be at most 5")
                Integer selfRating,
        String comments) {}
