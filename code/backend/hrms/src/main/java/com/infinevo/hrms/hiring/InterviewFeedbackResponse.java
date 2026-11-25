package com.infinevo.hrms.hiring;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing submitted interview scorecard (W-112).
 */
public record InterviewFeedbackResponse(
        UUID id,
        UUID interviewId,
        UUID interviewerId,
        String interviewerName,
        int rating,
        ScorecardRecommendation recommendation,
        String strengths,
        String weaknesses,
        String comments,
        Instant submittedAt) {}
