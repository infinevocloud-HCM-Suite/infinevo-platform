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
        UUID policyId,
        String wfhYear,
        LocalDate yearStartDate,
        LocalDate yearEndDate,
        BigDecimal entitlementDays,
        BigDecimal proRateFactor,
        Instant createdAt,
        Instant updatedAt) {

    public static WfhAllocationResponse from(WfhAllocation entity, String employeeName, String employeeNumber) {
        return new WfhAllocationResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                employeeName,
                employeeNumber,
                entity.getPolicyId(),
                entity.getWfhYear(),
                entity.getYearStartDate(),
                entity.getYearEndDate(),
                entity.getEntitlementDays(),
                entity.getProRateFactor(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
