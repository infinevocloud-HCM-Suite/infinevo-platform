package com.infinevo.payroll.tds.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an active employee TDS record is not found (W-36.1 §4).
 * Maps to HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EmployeeTdsNotFoundException extends RuntimeException {

    public EmployeeTdsNotFoundException(UUID employeeId, String fy) {
        super("No active TDS record found for employee " + employeeId + " in financial year " + fy);
    }

    public EmployeeTdsNotFoundException(String message) {
        super(message);
    }
}
