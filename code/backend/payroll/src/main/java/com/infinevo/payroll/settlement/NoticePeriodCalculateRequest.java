package com.infinevo.payroll.settlement;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for calculating notice period shortfall / payout without persisting (W-90.2).
 */
public record NoticePeriodCalculateRequest(
        @NotNull(message = "Employee ID is required") UUID employeeId,
        @NotNull(message = "Resignation date is required") LocalDate resignationDate,
        @NotNull(message = "Actual exit date is required") LocalDate actualExitDate,
        Integer requiredNoticeDays,
        String rateBasis,
        Integer dailyDivisor,
        boolean isWaived,
        String waiverReason) {

    public NoticePeriodCalculateRequest {
        if (requiredNoticeDays == null || requiredNoticeDays <= 0) {
            requiredNoticeDays = 30;
        }
        if (rateBasis == null || rateBasis.isBlank()) {
            rateBasis = "GROSS";
        }
        if (dailyDivisor == null || dailyDivisor <= 0) {
            dailyDivisor = 30;
        }
    }
}
