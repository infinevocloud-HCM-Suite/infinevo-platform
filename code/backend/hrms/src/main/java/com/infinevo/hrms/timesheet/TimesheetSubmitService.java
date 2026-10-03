package com.infinevo.hrms.timesheet;

import java.util.UUID;

/**
 * Sends a timesheet for approval and sends rejected projects back (W-42.3). Approving and rejecting are not here: they
 * are {@code core}'s decide endpoint, whose outcome {@link TimesheetOutcomeHandler} writes back onto the project line.
 *
 * <p>Approval is per project: each project line goes for approval on its own, as its own approval instance, to that
 * project's manager ({@link TimesheetProjectManagerResolver}). Like every timesheet service, this works for the
 * employee behind the login and nobody else; a timesheet that is not the caller's is "not found".
 */
public interface TimesheetSubmitService {

    /** The subject table the approval instances name: one instance per project line, whose id is the subject. */
    String SUBJECT_TABLE = "hrms.timesheet_project_entry";

    /**
     * Submits a draft week: the week and every project line become {@code SUBMITTED}, and an approval instance starts
     * for each line. All or nothing: if an instance cannot start, for example because no {@code TIMESHEET} approval
     * definition is in force, the week stays a draft.
     *
     * @throws com.infinevo.hrms.project.ResourceNotFoundException when it is not the caller's (404)
     * @throws TimesheetConflictException unless the week and every line are drafts, or when no approval can start (409)
     */
    TimesheetResponse submit(UUID id);

    /**
     * Replaces the rejected projects of a {@code REJECTED} week and sends only those back for approval, each as a new
     * instance; the old instances stay as history. The body carries only the rejected projects, with the rules of a
     * draft save, the 24 hours a day included across the whole week. A rejected project missing from the body stays
     * rejected.
     *
     * @throws com.infinevo.hrms.project.ResourceNotFoundException when it is not the caller's (404)
     * @throws TimesheetConflictException unless the week is rejected, or when the body names a project whose line is
     *     not rejected (409)
     * @throws com.infinevo.hrms.project.ValidationException when the body breaks a rule (400)
     */
    TimesheetResponse resubmit(UUID id, TimesheetRequest request);
}
