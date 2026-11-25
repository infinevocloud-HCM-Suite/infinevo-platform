package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload to create candidate profile (W-112).
 */
public record CandidateRequest(
        @NotBlank(message = "firstName is required") String firstName,
        @NotBlank(message = "lastName is required") String lastName,
        @NotBlank(message = "email is required") @Email(message = "Invalid email format") String email,
        String phone,
        String currentCompany,
        String currentTitle,
        BigDecimal experienceYears,
        Integer noticePeriodDays,
        UUID resumeDocumentId) {}
