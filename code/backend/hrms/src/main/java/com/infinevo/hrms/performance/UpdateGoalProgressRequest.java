package com.infinevo.hrms.performance;

import java.math.BigDecimal;

/**
 * Request payload to update goal progress or status (W-106.2).
 */
public record UpdateGoalProgressRequest(
        BigDecimal currentValue, Integer progressPercentage, GoalStatus status, String progressNotes) {}
