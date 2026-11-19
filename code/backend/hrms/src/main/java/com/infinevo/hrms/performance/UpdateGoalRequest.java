package com.infinevo.hrms.performance;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateGoalRequest(
        @Size(max = 200, message = "Goal title must not exceed 200 characters") String title,
        String description,
        GoalCategory category,
        BigDecimal targetValue,
        String metricUnit,
        LocalDate startDate,
        LocalDate dueDate,
        UUID cycleId) {}
