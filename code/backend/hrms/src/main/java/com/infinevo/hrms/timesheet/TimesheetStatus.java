package com.infinevo.hrms.timesheet;

/**
 * Status of a timesheet and of each of its project lines (W-42.1), as the frozen HRMS enumeration
 * ({@code legacy/HRMS_Backend/.../enumuration/TimesheetStatus.java}).
 *
 * <p>W-42.1 writes only {@link #DRAFT}. The rest belong to W-42.3 (submit and approve). {@link #CANCELLED} stays in
 * the set for legacy rows that W-67 may bring across; nothing here cancels a timesheet, because legacy's cancel
 * worked in any state (a deleted draft is simply gone).
 */
public enum TimesheetStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED
}
