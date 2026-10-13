package com.infinevo.shared.authz;

import org.springframework.security.access.AccessDeniedException;

/**
 * The signed-in account holds the action but has no employee record to act for (D-91).
 *
 * <p>Self-service endpoints resolve "me" through the employee record linked to the account. A tenant
 * administrator or platform user with no such record used to get {@code PermissionDeniedException},
 * which the API reports as "Not permitted: requires action 'hrms.timesheet.read_own'" — wrong, since
 * the action is held. This says what is actually missing. Still a 403: the caller may not act as an
 * employee, just not for the reason the old message gave.
 */
public class NoEmployeeRecordException extends AccessDeniedException {

    public static final String MESSAGE =
            "No employee record is linked to your account, so there is nothing to show here. Ask HR to link one.";

    public NoEmployeeRecordException() {
        super(MESSAGE);
    }
}
