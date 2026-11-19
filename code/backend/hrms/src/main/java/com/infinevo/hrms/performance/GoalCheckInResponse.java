package com.infinevo.hrms.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GoalCheckInResponse(
        UUID id,
        UUID goalId,
        BigDecimal currentValue,
        int progressPercentage,
        String notes,
        Instant createdAt,
        String createdBy) {

    public static GoalCheckInResponse from(EmployeeGoalCheckIn checkIn) {
        return new GoalCheckInResponse(
                checkIn.getId(),
                checkIn.getGoalId(),
                checkIn.getCurrentValue(),
                checkIn.getProgressPercentage(),
                checkIn.getNotes(),
                checkIn.getCreatedAt(),
                checkIn.getCreatedBy());
    }
}
