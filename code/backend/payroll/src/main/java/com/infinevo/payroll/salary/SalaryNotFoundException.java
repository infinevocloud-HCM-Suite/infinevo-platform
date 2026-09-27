package com.infinevo.payroll.salary;

import java.io.Serial;
import java.util.UUID;

/**
 * Exception thrown when a requested salary entity or version is not found (404 Not Found) (W-26.2).
 */
public class SalaryNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SalaryNotFoundException(String entity, UUID id) {
        super(entity + " with ID " + id + " was not found in this tenant");
    }

    public SalaryNotFoundException(String message) {
        super(message);
    }
}
