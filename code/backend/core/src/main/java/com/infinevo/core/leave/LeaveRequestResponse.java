package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response payload representing a leave request (W-16.3, spec section 4).
 */
public record LeaveRequestResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        UUID leaveTypeId,
        LocalDate fromDate,
        LocalDate toDate,
        boolean isHalfDay,
        HalfDayPeriod halfDayPeriod,
        BigDecimal workingDays,
        String reason,
        LeaveRequestStatus status,
        UUID approvalInstanceId,
        boolean onBehalf,
        Instant decidedAt,
        List<UUID> documentIds,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static LeaveRequestResponse from(LeaveRequest req, List<UUID> documentIds) {
        return new LeaveRequestResponse(
                req.getId(),
                req.getTenantId(),
                req.getEmployeeId(),
                req.getLeaveTypeId(),
                req.getFromDate(),
                req.getToDate(),
                req.isHalfDay(),
                req.getHalfDayPeriod(),
                req.getWorkingDays(),
                req.getReason(),
                req.getStatus(),
                req.getApprovalInstanceId(),
                req.isOnBehalf(),
                req.getDecidedAt(),
                documentIds != null ? documentIds : List.of(),
                req.getCreatedAt(),
                req.getCreatedBy(),
                req.getUpdatedAt(),
                req.getUpdatedBy());
    }
}
