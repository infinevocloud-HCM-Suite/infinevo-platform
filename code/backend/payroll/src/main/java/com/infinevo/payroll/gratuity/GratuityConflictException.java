package com.infinevo.payroll.gratuity;

/**
 * Thrown when an active gratuity settlement already exists for the employee (W-91).
 */
public class GratuityConflictException extends RuntimeException {
    public GratuityConflictException(String message) {
        super(message);
    }
}
