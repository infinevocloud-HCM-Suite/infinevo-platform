package com.infinevo.core.attendance;

/**
 * The source of an attendance record (W-39.1).
 *
 * <p>{@link #ADMIN} is used for administrator-captured attendance in Core.
 * {@link #CLOCK} is reserved for future clock-in/clock-out capture in HRMS (W-40).
 */
public enum AttendanceSource {
    ADMIN,
    CLOCK
}
