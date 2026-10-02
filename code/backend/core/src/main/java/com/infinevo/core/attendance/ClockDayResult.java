package com.infinevo.core.attendance;

/**
 * Result of writing an attendance day from the clock seam (W-40.2).
 *
 * @param written whether the day was written/updated by this call (false if an ADMIN row was left unchanged)
 * @param status the attendance status held by the day after the call
 * @param source the source held by the day after the call (CLOCK if written, ADMIN if unchanged)
 */
public record ClockDayResult(boolean written, AttendanceStatus status, AttendanceSource source) {}
