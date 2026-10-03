package com.infinevo.hrms.timesheet;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only review lists for the people who approve or oversee timesheets (W-42.4). Drafts are never listed: they are
 * the owner's alone. Nothing here decides; that is {@code core}'s decide endpoint (W-42.3).
 *
 * <p>The caller is the employee behind the login; a login with no employee record is the permission error. Which
 * action a list needs is the controller's guard. What the caller sees <em>of a week</em> is
 * {@link TimesheetAccessResolver}'s.
 *
 * <p>Common to the lists: {@code from} and {@code to} bound the week start, both inclusive; {@code status} is
 * {@code SUBMITTED}, {@code APPROVED} or {@code REJECTED} (anything else is a validation error); {@code size} is
 * capped at 100. Newest week first, then employee.
 */
public interface TimesheetReviewService {

    /**
     * A project manager's list: weeks with a line on a project the caller manages, trimmed to those lines.
     *
     * @param projectId only this project, which must be one the caller manages, or {@code null}
     * @throws com.infinevo.hrms.project.ValidationException on a bad filter, or a project the caller does not manage
     */
    TimesheetPage managed(LocalDate from, LocalDate to, TimesheetStatus status, UUID projectId, int page, int size);

    /**
     * A reporting manager's list: the whole week of each direct report.
     *
     * @param employeeId only this employee, who must be a direct report, or {@code null}
     * @throws com.infinevo.hrms.project.ValidationException on a bad filter, or an employee who is not a direct report
     */
    TimesheetPage team(LocalDate from, LocalDate to, TimesheetStatus status, UUID employeeId, int page, int size);

    /** HR's list: every non-draft week in the tenant, whole. */
    TimesheetPage all(
            LocalDate from, LocalDate to, TimesheetStatus status, UUID employeeId, UUID projectId, int page, int size);

    /**
     * One week as the caller may see it: their own, or, by {@link TimesheetAccessResolver}, a whole or trimmed week of
     * someone else's.
     *
     * @throws com.infinevo.hrms.project.ResourceNotFoundException when there is no such week or the caller sees none
     *     of it, so ids do not leak (404)
     */
    TimesheetResponse get(UUID id);
}
