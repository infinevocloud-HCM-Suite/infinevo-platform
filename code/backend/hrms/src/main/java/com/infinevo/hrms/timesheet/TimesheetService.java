package com.infinevo.hrms.timesheet;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The caller's own timesheets (W-42.1). Nothing here submits or approves: that is W-42.3.
 *
 * <p>Every method works for the employee behind the login and nobody else. A login with no employee record is refused
 * with the permission error, never a server error. A timesheet that is not the caller's is "not found".
 */
public interface TimesheetService {

    /**
     * Saves a new draft week. The employee is the caller.
     *
     * @throws com.infinevo.hrms.project.ValidationException when the body breaks a rule, including a project the
     *     caller is not assigned to or a task that is not on that project (400)
     * @throws TimesheetConflictException when the caller already has a non-cancelled timesheet for that week (409)
     */
    TimesheetResponse create(TimesheetRequest request);

    /**
     * Replaces every entry of a draft; the week cannot change.
     *
     * @throws com.infinevo.hrms.project.ResourceNotFoundException when it is not the caller's (404)
     * @throws TimesheetConflictException unless it is a draft (409)
     * @throws com.infinevo.hrms.project.ValidationException when the body breaks a rule (400)
     */
    TimesheetResponse replace(UUID id, TimesheetRequest request);

    /**
     * Removes a draft and all its entries for good.
     *
     * @throws com.infinevo.hrms.project.ResourceNotFoundException when it is not the caller's (404)
     * @throws TimesheetConflictException unless it is a draft (409)
     */
    void delete(UUID id);

    /** One of the caller's timesheets, nested. {@code ResourceNotFoundException} when it is not theirs. */
    TimesheetResponse get(UUID id);

    /**
     * The caller's timesheets, newest week first.
     *
     * @param from first week start to include, or {@code null}
     * @param to last week start to include, or {@code null}
     * @param status only this status, or {@code null}
     * @param projectId only timesheets with a line for this project, or {@code null}
     */
    List<TimesheetResponse> listMine(LocalDate from, LocalDate to, TimesheetStatus status, UUID projectId);

    /**
     * The caller's live timesheet for a week, or empty when there is none.
     *
     * @param weekStart the Monday, or {@code null} for this week's
     */
    Optional<TimesheetResponse> forWeek(LocalDate weekStart);
}
