package com.infinevo.hrms.hiring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a candidate (W-112).
 */
public record CandidateResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String currentCompany,
        String currentTitle,
        BigDecimal experienceYears,
        Integer noticePeriodDays,
        UUID resumeDocumentId,
        Instant createdAt,
        Instant updatedAt) {}
