package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Breakdown of an employee's leave balance for a specific leave type and year (W-16.2).
 * Shows the working: remaining = entitlement + accrued + carried_forward - consumed.
 */
public record LeaveBalanceResponse(
        UUID employeeId,
        UUID leaveTypeId,
        String leaveTypeCode,
        String leaveTypeName,
        String leaveYear,
        BigDecimal entitlementDays,
        BigDecimal accruedDays,
        BigDecimal carriedForwardDays,
        BigDecimal consumedDays,
        BigDecimal remainingDays,
        LocalDate carryForwardExpiresOn) {}
