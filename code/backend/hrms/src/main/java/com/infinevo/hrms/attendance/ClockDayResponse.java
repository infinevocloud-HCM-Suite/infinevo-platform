package com.infinevo.hrms.attendance;

import com.infinevo.core.attendance.AttendanceStatus;
import java.time.LocalDate;

/**
 * Result of deriving a day's attendance status from clock sessions (W-40.3).
 */
public record ClockDayResponse(
        LocalDate date, int workedMinutes, AttendanceStatus status, boolean writtenToAttendance) {}
