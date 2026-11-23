package com.infinevo.payroll.settlement;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to initiate a Full & Final settlement computation (W-90.3).
 */
public record FnfInitiateRequest(
        @NotNull(message = "Employee ID is required") UUID employeeId,
        LocalDate resignationDate,
        @NotNull(message = "Exit date is required") LocalDate exitDate,
        String exitReason,
        Integer workingDaysInExitMonth,
        BigDecimal otherEarnings,
        BigDecimal otherRecoveries,
        String remarks) {}
