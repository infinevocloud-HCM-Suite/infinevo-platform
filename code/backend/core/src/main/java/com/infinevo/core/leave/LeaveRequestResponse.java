package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response payload representing a leave request (W-16.3, spec section 4). D-80: the employee's and
 * leave type's names ride beside their ids, read in one batch per list by the service, so no screen
 * needs to load every employee or leave type to label a row; null when not looked up.
 */
public record LeaveRequestResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        String employeeName,
        UUID leaveTypeId,
        String leaveTypeName,
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
        return from(req, documentIds, null, null);
    }

    /** With the employee's and leave type's display names (D-80). */
    public static LeaveRequestResponse from(
            LeaveRequest req, List<UUID> documentIds, String employeeName, String leaveTypeName) {
        return new LeaveRequestResponse(
                req.getId(),
                req.getTenantId(),
                req.getEmployeeId(),
                employeeName,
                req.getLeaveTypeId(),
                leaveTypeName,
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
