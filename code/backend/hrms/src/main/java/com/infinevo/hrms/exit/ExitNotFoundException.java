package com.infinevo.hrms.exit;

/**
 * Thrown when an exit request or clearance item is not found in the current tenant (W-103).
 */
public class ExitNotFoundException extends RuntimeException {
    public ExitNotFoundException(String message) {
        super(message);
    }
}
