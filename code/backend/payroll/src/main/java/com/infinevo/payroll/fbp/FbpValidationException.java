package com.infinevo.payroll.fbp;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;

/**
 * Thrown when FBP plan validation fails (W-27.1).
 */
public class FbpValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fieldErrors;

    public FbpValidationException(Map<String, String> fieldErrors) {
        super("FBP validation failed: " + fieldErrors);
        this.fieldErrors = Collections.unmodifiableMap(fieldErrors);
    }

    public FbpValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
