package com.infinevo.payroll.gratuity;

/**
 * Thrown when a requested gratuity settlement does not exist in the current tenant (W-91).
 */
public class GratuityNotFoundException extends RuntimeException {
    public GratuityNotFoundException(String message) {
        super(message);
    }
}
