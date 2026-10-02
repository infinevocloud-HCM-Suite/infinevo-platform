package com.infinevo.payroll.tds.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when another request recorded the same employee's year at the same moment (W-36.1 §4).
 * Maps to HTTP 409 Conflict; the caller retries.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class EmployeeTdsConflictException extends RuntimeException {

    public EmployeeTdsConflictException(UUID employeeId, String financialYear) {
        super("Another TDS record for employee " + employeeId + " and financial year " + financialYear
                + " was saved at the same time; retry");
    }
}
