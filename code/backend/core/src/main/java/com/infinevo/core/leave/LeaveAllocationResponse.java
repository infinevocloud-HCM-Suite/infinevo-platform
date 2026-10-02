package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response representation of a leave allocation entity (W-16.2).
 */
public record LeaveAllocationResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        UUID leaveTypeId,
        String leaveYear,
        LocalDate yearStartDate,
        LocalDate yearEndDate,
        BigDecimal entitlementDays,
        BigDecimal accruedDays,
        BigDecimal carriedForwardDays,
        LocalDate carryForwardExpiresOn,
        BigDecimal proRateFactor,
        LocalDate lastAccruedOn,
        LocalDate lastResetOn,
        UUID policyId) {

    public static LeaveAllocationResponse from(LeaveAllocation entity) {
        if (entity == null) {
            return null;
        }
        return new LeaveAllocationResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                entity.getLeaveTypeId(),
                entity.getLeaveYear(),
                entity.getYearStartDate(),
                entity.getYearEndDate(),
                entity.getEntitlementDays(),
                entity.getAccruedDays(),
                entity.getCarriedForwardDays(),
                entity.getCarryForwardExpiresOn(),
                entity.getProRateFactor(),
                entity.getLastAccruedOn(),
                entity.getLastResetOn(),
                entity.getPolicyId());
    }
}
