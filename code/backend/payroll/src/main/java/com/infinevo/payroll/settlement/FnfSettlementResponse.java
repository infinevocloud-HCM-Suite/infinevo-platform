package com.infinevo.payroll.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload carrying full consolidated F&F settlement calculations (W-90.3).
 */
public record FnfSettlementResponse(
        UUID id,
        UUID employeeId,
        LocalDate resignationDate,
        LocalDate exitDate,
        String exitReason,
        int workingDaysInExitMonth,
        BigDecimal exitMonthSalary,
        BigDecimal leaveEncashmentAmount,
        BigDecimal gratuityAmount,
        BigDecimal noticePayAmount,
        BigDecimal otherEarnings,
        BigDecimal grossEarnings,
        BigDecimal noticeRecoveryAmount,
        BigDecimal epfDeduction,
        BigDecimal esiDeduction,
        BigDecimal ptDeduction,
        BigDecimal tdsDeduction,
        BigDecimal otherRecoveries,
        BigDecimal grossDeductions,
        BigDecimal netPayable,
        String status,
        String approvedBy,
        Instant approvedAt,
        Instant paidAt,
        String remarks,
        Instant createdAt) {}
