package com.infinevo.hrms.hiring;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a candidate job application (W-112).
 */
public record JobApplicationResponse(
        UUID id,
        UUID jobOpeningId,
        String jobOpeningTitle,
        String jobOpeningCode,
        UUID candidateId,
        String candidateName,
        String candidateEmail,
        String candidatePhone,
        UUID resumeDocumentId,
        JobApplicationStage stage,
        String rejectionReason,
        Instant appliedAt,
        Instant updatedAt) {}
