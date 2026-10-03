package com.infinevo.hrms.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An attendance regularization request as the API returns it (W-40.4 §4).
 */
public record RegularizationResponse(
        UUID id,
        UUID employeeId,
        LocalDate date,
        Instant inAt,
        Instant outAt,
        String reason,
        RegularizationStatus status,
        UUID approvalInstanceId,
        Instant decidedAt,
        UUID decidedBy,
        String decisionComment,
        Instant createdAt) {

    public static RegularizationResponse from(AttendanceRegularization r) {
        return new RegularizationResponse(
                r.getId(),
                r.getEmployeeId(),
                r.getAttendanceDate(),
                r.getRequestedInAt(),
                r.getRequestedOutAt(),
                r.getReason(),
                r.getStatus(),
                r.getApprovalInstanceId(),
                r.getDecidedAt(),
                r.getDecidedBy(),
                r.getDecisionComment(),
                r.getCreatedAt());
    }
}
