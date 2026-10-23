package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WfhRequestResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        String employeeName,
        String employeeNumber,
        LocalDate fromDate,
        LocalDate toDate,
        boolean halfDay,
        String halfDayPeriod,
        BigDecimal workingDays,
        String reason,
        WfhRequestStatus status,
        UUID approvalInstanceId,
        boolean onBehalf,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static WfhRequestResponse from(WfhRequest entity, String employeeName, String employeeNumber) {
        return new WfhRequestResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                employeeName,
                employeeNumber,
                entity.getFromDate(),
                entity.getToDate(),
                entity.isHalfDay(),
                entity.getHalfDayPeriod(),
                entity.getWorkingDays(),
                entity.getReason(),
                entity.getStatus(),
                entity.getApprovalInstanceId(),
                entity.isOnBehalf(),
                entity.getDecidedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
