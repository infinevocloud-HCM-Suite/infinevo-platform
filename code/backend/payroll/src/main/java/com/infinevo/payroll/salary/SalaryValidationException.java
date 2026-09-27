package com.infinevo.payroll.salary;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception thrown when salary version data violates business rules (400 Bad Request) (W-26.2).
 */
public class SalaryValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, String> fieldErrors;

    public SalaryValidationException(String field, String message) {
        super(message);
        this.fieldErrors = Collections.singletonMap(field, message);
    }

    public SalaryValidationException(String message) {
        super(message);
        this.fieldErrors = Collections.singletonMap("general", message);
    }

    public SalaryValidationException(Map<String, String> fieldErrors) {
        super("Validation failed for salary structure: " + fieldErrors);
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
