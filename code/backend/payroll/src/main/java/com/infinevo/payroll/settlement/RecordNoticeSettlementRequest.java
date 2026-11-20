package com.infinevo.payroll.settlement;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for recording and saving a notice period settlement (W-90.2).
 */
public record RecordNoticeSettlementRequest(
        @NotNull(message = "Employee ID is required") UUID employeeId,
        @NotNull(message = "Resignation date is required") LocalDate resignationDate,
        @NotNull(message = "Actual exit date is required") LocalDate actualExitDate,
        Integer requiredNoticeDays,
        String rateBasis,
        Integer dailyDivisor,
        boolean isWaived,
        String waiverReason,
        String approvedBy,
        String status) {

    public RecordNoticeSettlementRequest {
        if (requiredNoticeDays == null || requiredNoticeDays <= 0) {
            requiredNoticeDays = 30;
        }
        if (rateBasis == null || rateBasis.isBlank()) {
            rateBasis = "GROSS";
        }
        if (dailyDivisor == null || dailyDivisor <= 0) {
            dailyDivisor = 30;
        }
        if (status == null || status.isBlank()) {
            status = "DRAFT";
        }
    }
}
