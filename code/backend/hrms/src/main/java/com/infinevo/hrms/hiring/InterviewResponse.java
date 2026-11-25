package com.infinevo.hrms.hiring;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing an interview (W-112).
 */
public record InterviewResponse(
        UUID id,
        UUID applicationId,
        UUID candidateId,
        String candidateName,
        UUID jobOpeningId,
        String jobOpeningTitle,
        String roundName,
        Instant scheduledAt,
        int durationMinutes,
        UUID interviewerId,
        String interviewerName,
        String meetingLink,
        InterviewStatus status,
        int feedbackCount,
        Double averageRating,
        Instant createdAt) {}
