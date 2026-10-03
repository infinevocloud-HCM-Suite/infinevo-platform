package com.infinevo.hrms.attendance;

/**
 * Strategy for calculating total daily work hours from attendance sessions (W-40.1).
 *
 * <ul>
 *   <li>{@link #FIRST_IN_LAST_OUT}: Difference between the day's first check-in and last check-out.</li>
 *   <li>{@link #EVERY_SESSION}: Sum of durations across all recorded clock sessions in the day.</li>
 * </ul>
 */
public enum HoursCalculation {
    FIRST_IN_LAST_OUT,
    EVERY_SESSION
}
