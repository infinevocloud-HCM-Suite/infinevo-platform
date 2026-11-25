package com.infinevo.payroll.gratuity;

import java.util.Map;

/**
 * Thrown when a gratuity request fails field-level validation (W-91).
 */
public class GratuityValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public GratuityValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
