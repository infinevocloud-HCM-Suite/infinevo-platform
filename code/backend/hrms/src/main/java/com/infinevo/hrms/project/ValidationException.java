package com.infinevo.hrms.project;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown when a project, task, or assignment request fails validation (maps to 400).
 */
public class ValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, String> fieldErrors;

    public ValidationException(String field, String error) {
        super(field + ": " + error);
        this.fieldErrors = Map.of(field, error);
    }

    public ValidationException(Map<String, String> fieldErrors) {
        super("Validation failed: " + fieldErrors);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
