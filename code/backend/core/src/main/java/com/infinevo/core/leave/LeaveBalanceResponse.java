package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Breakdown of an employee's leave balance for a specific leave type and year (W-16.2, D-138, D-139).
 * Shows the working: remaining = entitlement + accrued + carried_forward - consumed.
 * Available days = remaining - pending (clamped >= 0).
 * {@code expired} is true when the leave type is switched off or its Valid To has passed (W-77.1).
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
        LocalDate carryForwardExpiresOn,
        LocalDate leaveTypeValidTo,
        boolean expired,
        BigDecimal pendingDays,
        BigDecimal availableDays) {

    public LeaveBalanceResponse(
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
            LocalDate carryForwardExpiresOn,
            LocalDate leaveTypeValidTo,
            boolean expired) {
        this(
                employeeId,
                leaveTypeId,
                leaveTypeCode,
                leaveTypeName,
                leaveYear,
                entitlementDays,
                accruedDays,
                carriedForwardDays,
                consumedDays,
                remainingDays,
                carryForwardExpiresOn,
                leaveTypeValidTo,
                expired,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                remainingDays);
    }
}
