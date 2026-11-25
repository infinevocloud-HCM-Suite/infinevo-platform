package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to submit interview feedback scorecard (W-112).
 */
public record InterviewFeedbackRequest(
        @NotNull(message = "rating is required") @Min(1) @Max(5) Integer rating,
        @NotNull(message = "recommendation is required") ScorecardRecommendation recommendation,
        String strengths,
        String weaknesses,
        String comments) {}
