package com.infinevo.payroll.schedule;

/**
 * Rule governing which day of the month pay lands on (W-28 §4).
 */
public enum PayDayRule {
    /** Pay on the last calendar day of the pay period month. */
    LAST_DAY_OF_PERIOD,

    /** Pay on the last working day (matching tenant's working weekdays) of the pay period month. */
    LAST_WORKING_DAY,

    /** Pay on a specific calendar day (1–28) of the month following the pay period. */
    SPECIFIC_DAY
}
