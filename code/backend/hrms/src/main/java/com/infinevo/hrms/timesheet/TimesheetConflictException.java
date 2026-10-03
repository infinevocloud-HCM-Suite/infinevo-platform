package com.infinevo.hrms.timesheet;

import java.io.Serial;

/**
 * Thrown when a timesheet request conflicts with the timesheet's state (maps to 409): the caller already has one for
 * that week, or the timesheet is not a draft and so cannot be replaced or deleted.
 */
public class TimesheetConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TimesheetConflictException(String message) {
        super(message);
    }
}
