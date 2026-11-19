package com.infinevo.hrms.performance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to create an employee goal or OKR (W-106.2).
 */
public record CreateGoalRequest(
        @NotBlank(message = "Goal title is required")
                @Size(max = 200, message = "Goal title must not exceed 200 characters")
                String title,
        String description,
        GoalCategory category,
        MetricType metricType,
        BigDecimal targetValue,
        BigDecimal currentValue,
        String metricUnit,
        Integer progressPercentage,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "Due date is required") LocalDate dueDate,
        UUID cycleId,
        GoalStatus status) {}
