package com.itsdev.payroll.exception;

/**
 * Thrown when a requested resource does not exist or is not accessible
 * to the requesting employee (e.g., reimbursement request not found for
 * this employee+organization combination).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " with id " + id + " not found.");
    }
}
