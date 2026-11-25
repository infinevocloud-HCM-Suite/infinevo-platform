package com.infinevo.hrms.hiring;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload representing a job opening with stage counts (W-112).
 */
public record JobOpeningResponse(
        UUID id,
        String openingCode,
        String title,
        UUID departmentId,
        String departmentName,
        UUID designationId,
        String designationTitle,
        UUID workLocationId,
        String workLocationName,
        UUID hiringManagerId,
        String hiringManagerName,
        JobOpeningEmploymentType employmentType,
        int openingsCount,
        int filledCount,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        Integer minExperienceYears,
        Integer maxExperienceYears,
        String description,
        JobOpeningStatus status,
        LocalDate targetDate,
        long totalCandidates,
        long appliedCount,
        long screeningCount,
        long interviewingCount,
        long offeredCount,
        long hiredCount,
        long rejectedCount,
        Instant createdAt,
        Instant updatedAt) {}
