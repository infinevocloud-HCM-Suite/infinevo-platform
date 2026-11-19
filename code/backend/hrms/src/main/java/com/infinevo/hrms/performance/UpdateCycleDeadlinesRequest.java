package com.infinevo.hrms.performance;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UpdateCycleDeadlinesRequest(
        @NotNull(message = "selfReviewDeadline is required") LocalDate selfReviewDeadline,
        @NotNull(message = "managerReviewDeadline is required") LocalDate managerReviewDeadline) {}
