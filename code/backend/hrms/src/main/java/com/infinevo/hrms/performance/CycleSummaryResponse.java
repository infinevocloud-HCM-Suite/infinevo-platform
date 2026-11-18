package com.infinevo.hrms.performance;

import java.util.UUID;

/**
 * Summary metrics for a review cycle (W-106.1).
 */
public record CycleSummaryResponse(
        UUID cycleId,
        String cycleName,
        PerformanceCycleStatus status,
        long totalReviews,
        long completedReviews,
        long pendingSelfReviews,
        long pendingManagerReviews,
        double completionPercentage,
        Double averageRating) {}
