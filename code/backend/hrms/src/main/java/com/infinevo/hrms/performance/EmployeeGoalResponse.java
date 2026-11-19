package com.infinevo.hrms.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response projection for an employee goal / OKR (W-106.2).
 */
public record EmployeeGoalResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        String employeeNumber,
        UUID cycleId,
        String cycleName,
        String title,
        String description,
        GoalCategory category,
        MetricType metricType,
        BigDecimal targetValue,
        BigDecimal currentValue,
        String metricUnit,
        int progressPercentage,
        LocalDate startDate,
        LocalDate dueDate,
        GoalStatus status,
        boolean readOnly,
        Instant createdAt,
        Instant updatedAt) {

    public static EmployeeGoalResponse from(
            EmployeeGoal goal, String employeeName, String employeeNumber, String cycleName, boolean readOnly) {
        return new EmployeeGoalResponse(
                goal.getId(),
                goal.getEmployeeId(),
                employeeName,
                employeeNumber,
                goal.getCycleId(),
                cycleName,
                goal.getTitle(),
                goal.getDescription(),
                goal.getCategory(),
                goal.getMetricType(),
                goal.getTargetValue(),
                goal.getCurrentValue(),
                goal.getMetricUnit(),
                goal.getProgressPercentage(),
                goal.getStartDate(),
                goal.getDueDate(),
                goal.getStatus(),
                readOnly,
                goal.getCreatedAt(),
                goal.getUpdatedAt());
    }

    public static EmployeeGoalResponse from(
            EmployeeGoal goal, String employeeName, String employeeNumber, String cycleName) {
        return from(goal, employeeName, employeeNumber, cycleName, goal.isFinal());
    }
}
