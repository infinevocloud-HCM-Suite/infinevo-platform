package com.infinevo.payroll.settlement;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for estimating leave encashment at exit (W-90.1).
 */
public record LeaveEncashmentEstimateRequest(
        @NotNull(message = "Employee ID is required") UUID employeeId,
        @NotNull(message = "Exit date is required") LocalDate exitDate,
        UUID leaveTypeId,
        BigDecimal customDays,
        Integer dailyDivisor) {

    public LeaveEncashmentEstimateRequest {
        if (dailyDivisor == null || dailyDivisor <= 0) {
            dailyDivisor = 30;
        }
    }
}
