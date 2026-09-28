package com.infinevo.core.attendance;

import java.time.LocalDate;

/**
 * Attendance day record returned by {@link AttendanceQuery} (W-39.1).
 */
public record AttendanceDay(LocalDate date, AttendanceStatus status, AttendanceSource source, String remarks) {}
