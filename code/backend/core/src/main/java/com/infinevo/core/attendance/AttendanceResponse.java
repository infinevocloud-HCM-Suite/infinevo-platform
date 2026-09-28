package com.infinevo.core.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An attendance record as returned by the API (W-39.1).
 */
public record AttendanceResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        LocalDate date,
        AttendanceStatus status,
        AttendanceSource source,
        String remarks,
        Instant createdAt,
        Instant updatedAt) {

    public static AttendanceResponse from(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getTenantId(),
                attendance.getEmployee().getId(),
                attendance.getAttendanceDate(),
                attendance.getStatus(),
                attendance.getSource(),
                attendance.getRemarks(),
                attendance.getCreatedAt(),
                attendance.getUpdatedAt());
    }
}
