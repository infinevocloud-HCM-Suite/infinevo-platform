package com.infinevo.hrms.performance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Request payload to create a performance review cycle (W-106.1).
 */
public record CreateCycleRequest(
        @NotBlank(message = "Cycle name is required") String name,
        @NotNull(message = "periodStart is required") LocalDate periodStart,
        @NotNull(message = "periodEnd is required") LocalDate periodEnd,
        @NotNull(message = "selfReviewDeadline is required") LocalDate selfReviewDeadline,
        @NotNull(message = "managerReviewDeadline is required") LocalDate managerReviewDeadline,
        Integer ratingScaleMax) {}
