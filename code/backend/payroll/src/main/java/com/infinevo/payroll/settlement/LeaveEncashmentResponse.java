package com.infinevo.payroll.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload representing leave encashment calculations and statutory exemption (W-90.1).
 */
public record LeaveEncashmentResponse(
        UUID id,
        UUID employeeId,
        UUID leaveTypeId,
        String leaveTypeCode,
        String leaveTypeName,
        LocalDate exitDate,
        BigDecimal encashableDays,
        BigDecimal perDayRate,
        BigDecimal monthlyBasicAndDa,
        int dailyDivisor,
        BigDecimal grossEncashmentAmount,
        BigDecimal taxExemptAmount,
        BigDecimal taxableAmount,
        BigDecimal statutoryCeiling,
        String status) {}
