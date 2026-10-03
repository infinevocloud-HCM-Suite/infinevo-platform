package com.infinevo.hrms.project;

import java.util.UUID;

/**
 * Asks whether a project or a task is on a live timesheet (W-42.1, W-41 §4).
 *
 * <p>W-41 refuses to delete a project or a task for as long as any timesheet that is not cancelled has a line for it:
 * the hours were worked, and a timesheet that cannot say on what is not worth keeping. Timesheets live in the
 * {@code timesheet} package, which depends on this one, so the question is an interface here and the answer is there.
 */
public interface TimesheetUsage {

    /** Whether any non-cancelled timesheet has a line for this project. */
    boolean projectInUse(UUID tenantId, UUID projectId);

    /** Whether any non-cancelled timesheet has a line for this task. */
    boolean taskInUse(UUID tenantId, UUID taskId);
}
