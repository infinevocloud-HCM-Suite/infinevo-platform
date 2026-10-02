package com.infinevo.hrms.timesheet;

import java.util.UUID;

/**
 * The approver's read of the one project line they have been asked to decide (W-42.3 §4). The approver's inbox is
 * {@code core}'s pending list, whose item id is this line's id.
 */
public interface TimesheetProjectEntryService {

    /**
     * The line, to the employee assigned a step on any approval instance started for it, and to nobody else.
     *
     * @throws com.infinevo.hrms.project.ResourceNotFoundException when there is no such line or the caller is not
     *     assigned a step for it, so ids do not leak (404)
     */
    TimesheetProjectEntryResponse get(UUID entryId);
}
