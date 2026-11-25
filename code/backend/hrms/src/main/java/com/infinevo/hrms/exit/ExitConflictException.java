package com.infinevo.hrms.exit;

/**
 * Thrown when an exit state transition conflicts with the current workflow state (W-103).
 */
public class ExitConflictException extends RuntimeException {
    public ExitConflictException(String message) {
        super(message);
    }
}
