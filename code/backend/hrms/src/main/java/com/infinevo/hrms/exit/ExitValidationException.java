package com.infinevo.hrms.exit;

import java.util.Map;

/**
 * Thrown when an exit request fails field-level validation (W-103).
 */
public class ExitValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ExitValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
