package com.infinevo.hrms.performance;

import java.util.List;
import java.util.UUID;

/**
 * Service for performance review cycles, employee self-reviews, and manager appraisals (W-106.1).
 */
public interface PerformanceReviewService {

    PerformanceCycleResponse createCycle(CreateCycleRequest request);

    List<PerformanceCycleResponse> listCycles();

    PerformanceCycleResponse getCycle(UUID cycleId);

    PerformanceCycleResponse activateCycle(UUID cycleId);

    PerformanceCycleResponse closeCycle(UUID cycleId);

    CycleSummaryResponse getCycleSummary(UUID cycleId);

    List<PerformanceReviewResponse> getMyReviews();

    PerformanceReviewResponse submitSelfReview(UUID reviewId, SelfReviewRequest request);

    List<PerformanceReviewResponse> getTeamReviews();

    PerformanceCycleResponse updateCycleDeadlines(UUID cycleId, UpdateCycleDeadlinesRequest request);

    List<PerformanceReviewResponse> getCycleReviews(UUID cycleId);

    PerformanceReviewResponse reassignManager(UUID reviewId, ReassignManagerRequest request);

    PerformanceReviewResponse submitManagerReview(UUID reviewId, ManagerReviewRequest request);
}
