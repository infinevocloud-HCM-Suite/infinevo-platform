package com.infinevo.payroll.tds.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when employee TDS input fails validation rules (W-36.1 §4).
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmployeeTdsValidationException extends RuntimeException {

    public EmployeeTdsValidationException(String message) {
        super(message);
    }
}
