package com.infinevo.hrms.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An attendance regularization request as the API returns it (W-40.4 §4). D-79: {@code decidedByName}
 * names the employee who decided it, beside {@code decidedBy}.
 */
public record RegularizationResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        LocalDate date,
        Instant inAt,
        Instant outAt,
        String reason,
        RegularizationStatus status,
        UUID approvalInstanceId,
        Instant decidedAt,
        UUID decidedBy,
        String decidedByName,
        String decisionComment,
        Instant createdAt) {

    public static RegularizationResponse from(AttendanceRegularization r) {
        return from(r, (String) null);
    }

    /** With the employee's display name, as HR's list carries it (W-48.5 §4). */
    public static RegularizationResponse from(AttendanceRegularization r, String employeeName) {
        return from(r, employeeName, (String) null);
    }

    /**
     * With the employee and the decider named from {@code names} (D-79), read by the service in one batch;
     * an id the map does not hold gives a null name.
     */
    public static RegularizationResponse from(AttendanceRegularization r, java.util.Map<UUID, String> names) {
        java.util.Map<UUID, String> safe = names != null ? names : java.util.Map.of();
        return from(
                r,
                r.getEmployeeId() == null ? null : safe.get(r.getEmployeeId()),
                r.getDecidedBy() == null ? null : safe.get(r.getDecidedBy()));
    }

    /**
     * The owner's own view (W-48.5 §4): no {@code employeeName} — it is their own — but the decider named
     * from {@code names} (D-79).
     */
    public static RegularizationResponse ownView(AttendanceRegularization r, java.util.Map<UUID, String> names) {
        java.util.Map<UUID, String> safe = names != null ? names : java.util.Map.of();
        return from(r, null, r.getDecidedBy() == null ? null : safe.get(r.getDecidedBy()));
    }

    private static RegularizationResponse from(AttendanceRegularization r, String employeeName, String decidedByName) {
        return new RegularizationResponse(
                r.getId(),
                r.getEmployeeId(),
                employeeName,
                r.getAttendanceDate(),
                r.getRequestedInAt(),
                r.getRequestedOutAt(),
                r.getReason(),
                r.getStatus(),
                r.getApprovalInstanceId(),
                r.getDecidedAt(),
                r.getDecidedBy(),
                decidedByName,
                r.getDecisionComment(),
                r.getCreatedAt());
    }
}
