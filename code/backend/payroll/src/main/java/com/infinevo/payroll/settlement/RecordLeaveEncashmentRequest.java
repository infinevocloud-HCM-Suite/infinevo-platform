package com.infinevo.payroll.settlement;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for creating and persisting a leave encashment settlement (W-90.1).
 */
public record RecordLeaveEncashmentRequest(
        @NotNull(message = "Employee ID is required") UUID employeeId,
        @NotNull(message = "Exit date is required") LocalDate exitDate,
        @NotNull(message = "Leave type ID is required") UUID leaveTypeId,
        BigDecimal customDays,
        Integer dailyDivisor,
        String status) {

    public RecordLeaveEncashmentRequest {
        if (dailyDivisor == null || dailyDivisor <= 0) {
            dailyDivisor = 30;
        }
        if (status == null || status.isBlank()) {
            status = "DRAFT";
        }
    }
}
