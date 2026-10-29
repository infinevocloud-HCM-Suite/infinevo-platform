package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WfhAllocationResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        String employeeName,
        String employeeNumber,
        String departmentName,
        UUID policyId,
        String wfhYear,
        LocalDate yearStartDate,
        LocalDate yearEndDate,
        BigDecimal entitlementDays,
        BigDecimal consumedDays,
        BigDecimal balanceDays,
        BigDecimal proRateFactor,
        Instant createdAt,
        Instant updatedAt) {

    public static WfhAllocationResponse from(WfhAllocation entity, String employeeName, String employeeNumber) {
        return from(
                entity,
                employeeName,
                employeeNumber,
                null,
                BigDecimal.ZERO,
                entity.getEntitlementDays() != null ? entity.getEntitlementDays() : BigDecimal.ZERO);
    }

    public static WfhAllocationResponse from(
            WfhAllocation entity,
            String employeeName,
            String employeeNumber,
            String departmentName,
            BigDecimal consumedDays,
            BigDecimal balanceDays) {
        return new WfhAllocationResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                employeeName,
                employeeNumber,
                departmentName,
                entity.getPolicyId(),
                entity.getWfhYear(),
                entity.getYearStartDate(),
                entity.getYearEndDate(),
                entity.getEntitlementDays(),
                consumedDays != null ? consumedDays : BigDecimal.ZERO,
                balanceDays != null ? balanceDays : BigDecimal.ZERO,
                entity.getProRateFactor(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
