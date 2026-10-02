package com.infinevo.hrms.attendance;

import com.infinevo.core.attendance.AttendanceStatus;
import java.time.LocalDate;

/**
 * Clock-out response payload containing the closed session and the derived day attendance (W-40.3).
 */
public record ClockOutResponse(
        ClockSessionResponse session,
        LocalDate date,
        int workedMinutes,
        AttendanceStatus status,
        boolean writtenToAttendance) {

    public ClockOutResponse(ClockSessionResponse session, ClockDayResponse day) {
        this(session, day.date(), day.workedMinutes(), day.status(), day.writtenToAttendance());
    }
}
