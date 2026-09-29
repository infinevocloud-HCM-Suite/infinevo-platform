package com.infinevo.core.lop;

/**
 * Working-day basis deciding what a day of pay is worth (W-18.1, 12-core-contracts.md §5 row 10).
 *
 * <ul>
 *   <li>{@code ACTUAL_DAYS} — the calendar days in the period, the legacy divisor.
 *   <li>{@code ORG_DAYS} — the organisation's working days, configured or derived from working week.
 *   <li>{@code FIXED_30} — exactly 30 days per month.
 * </ul>
 */
public enum WorkingDayBasis {
    ACTUAL_DAYS,
    ORG_DAYS,
    FIXED_30
}
