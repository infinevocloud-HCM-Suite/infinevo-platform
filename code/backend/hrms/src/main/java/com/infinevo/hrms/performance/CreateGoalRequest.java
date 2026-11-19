package com.infinevo.hrms.performance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to create an employee goal or OKR (W-106.2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateGoalRequest(
        @NotBlank(message = "Goal title is required")
                @Size(max = 200, message = "Goal title must not exceed 200 characters")
                String title,
        String description,
        GoalCategory category,
        MetricType metricType,
        BigDecimal targetValue,
        String metricUnit,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "Due date is required") LocalDate dueDate,
        UUID cycleId,
        Boolean draft) {}
