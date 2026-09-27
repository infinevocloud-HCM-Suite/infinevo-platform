package com.infinevo.payroll.component;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;

/**
 * Thrown when component input validation fails (W-26.1).
 */
public class ComponentValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fieldErrors;

    public ComponentValidationException(Map<String, String> fieldErrors) {
        super("Validation failed: " + fieldErrors);
        this.fieldErrors = Collections.unmodifiableMap(fieldErrors);
    }

    public ComponentValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
