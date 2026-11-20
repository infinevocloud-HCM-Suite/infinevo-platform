package com.infinevo.payroll.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload representing notice period calculations and settlement direction (W-90.2).
 */
public record NoticePeriodResponse(
        UUID id,
        UUID employeeId,
        LocalDate resignationDate,
        LocalDate expectedExitDate,
        LocalDate actualExitDate,
        int requiredNoticeDays,
        int servedNoticeDays,
        int shortfallDays,
        BigDecimal dailyRate,
        String rateBasis,
        int dailyDivisor,
        BigDecimal baseSalaryAmount,
        String settlementType,
        BigDecimal settlementAmount,
        String waiverReason,
        String approvedBy,
        String status) {}
