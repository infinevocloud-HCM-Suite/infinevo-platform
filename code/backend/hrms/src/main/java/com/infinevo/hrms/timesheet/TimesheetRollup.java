package com.infinevo.hrms.timesheet;

import java.util.Collection;

/**
 * How a week's status follows from the statuses of its project lines (W-42.3 §4, "Roll-up"), ported as is from legacy
 * ({@code TimesheetServiceImpl.java:492-515}): any line {@code REJECTED} gives {@code REJECTED}; otherwise any line
 * {@code SUBMITTED} gives {@code SUBMITTED}; otherwise, when every line is {@code APPROVED}, {@code APPROVED}.
 *
 * <p>Pure, so each row is a unit test. A week with a line still in {@code DRAFT} (or none) is not settled by this rule:
 * the caller's current status stands.
 */
final class TimesheetRollup {

    private TimesheetRollup() {}

    /**
     * The week's status for the given line statuses.
     *
     * @param lines the status of every project line of the week
     * @param current the week's status now, returned when the lines do not decide it
     */
    static TimesheetStatus of(Collection<TimesheetStatus> lines, TimesheetStatus current) {
        if (lines.isEmpty() || lines.contains(TimesheetStatus.DRAFT)) {
            return current;
        }
        if (lines.contains(TimesheetStatus.REJECTED)) {
            return TimesheetStatus.REJECTED;
        }
        if (lines.contains(TimesheetStatus.SUBMITTED)) {
            return TimesheetStatus.SUBMITTED;
        }
        if (lines.stream().allMatch(s -> s == TimesheetStatus.APPROVED)) {
            return TimesheetStatus.APPROVED;
        }
        return current;
    }
}
