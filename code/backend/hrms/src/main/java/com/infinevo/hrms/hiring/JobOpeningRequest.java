package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to create or update a job opening (W-112).
 */
public record JobOpeningRequest(
        @NotBlank(message = "openingCode is required") String openingCode,
        @NotBlank(message = "title is required") String title,
        UUID departmentId,
        UUID designationId,
        UUID workLocationId,
        UUID hiringManagerId,
        JobOpeningEmploymentType employmentType,
        @NotNull(message = "openingsCount is required") Integer openingsCount,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        Integer minExperienceYears,
        Integer maxExperienceYears,
        String description,
        LocalDate targetDate) {}
