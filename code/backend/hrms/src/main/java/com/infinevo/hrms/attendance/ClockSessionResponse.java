package com.infinevo.hrms.attendance;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Clock session response payload (W-40.3).
 */
public record ClockSessionResponse(
        UUID id,
        UUID employeeId,
        LocalDate attendanceDate,
        Instant clockInAt,
        Instant clockOutAt,
        Integer workedMinutes,
        SessionOrigin origin,
        Instant voidedAt,
        VoidReason voidReason) {

    public static ClockSessionResponse from(ClockSession session) {
        Integer minutes = session.getClockOutAt() != null
                ? (int) Duration.between(session.getClockInAt(), session.getClockOutAt())
                        .toMinutes()
                : null;
        return new ClockSessionResponse(
                session.getId(),
                session.getEmployeeId(),
                session.getAttendanceDate(),
                session.getClockInAt(),
                session.getClockOutAt(),
                minutes,
                session.getOrigin(),
                session.getVoidedAt(),
                session.getVoidReason());
    }
}
