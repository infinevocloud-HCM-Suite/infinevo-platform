package com.infinevo.payroll.statutory.settings;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;

/**
 * Thrown when statutory setting validation fails (W-31.1, spec section 4).
 */
public class StatutorySettingsValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fieldErrors;

    public StatutorySettingsValidationException(Map<String, String> fieldErrors) {
        super("Validation failed: " + fieldErrors);
        this.fieldErrors = Collections.unmodifiableMap(fieldErrors);
    }

    public StatutorySettingsValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
