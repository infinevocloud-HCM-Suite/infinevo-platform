package com.infinevo.hrms.hiring;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload representing a formal job offer (W-112).
 */
public record JobOfferResponse(
        UUID id,
        UUID applicationId,
        UUID candidateId,
        String candidateName,
        String candidateEmail,
        UUID jobOpeningId,
        String jobOpeningTitle,
        UUID designationId,
        String designationTitle,
        UUID departmentId,
        String departmentName,
        UUID workLocationId,
        String workLocationName,
        BigDecimal offeredCtc,
        LocalDate targetJoinDate,
        LocalDate offerValidUntil,
        JobOfferStatus status,
        UUID convertedEmployeeId,
        Instant createdAt,
        Instant updatedAt) {}
