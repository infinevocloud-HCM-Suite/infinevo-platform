package com.infinevo.hrms.attendance;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Clock session response payload (W-40.3). {@code employeeName} is read for display at reply time (W-48.4 §4): filled
 * on the HR log ({@code allSessions}), null on the caller's own sessions.
 */
public record ClockSessionResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        LocalDate attendanceDate,
        Instant clockInAt,
        Instant clockOutAt,
        Integer workedMinutes,
        SessionOrigin origin,
        Instant voidedAt,
        VoidReason voidReason) {

    public static ClockSessionResponse from(ClockSession session) {
        return from(session, java.util.Map.of());
    }

    /** With the employee's name from the given map; a missing id leaves it null. */
    public static ClockSessionResponse from(ClockSession session, java.util.Map<UUID, String> employeeNames) {
        Integer minutes = session.getClockOutAt() != null
                ? (int) Duration.between(session.getClockInAt(), session.getClockOutAt())
                        .toMinutes()
                : null;
        return new ClockSessionResponse(
                session.getId(),
                session.getEmployeeId(),
                employeeNames.get(session.getEmployeeId()),
                session.getAttendanceDate(),
                session.getClockInAt(),
                session.getClockOutAt(),
                minutes,
                session.getOrigin(),
                session.getVoidedAt(),
                session.getVoidReason());
    }
}
