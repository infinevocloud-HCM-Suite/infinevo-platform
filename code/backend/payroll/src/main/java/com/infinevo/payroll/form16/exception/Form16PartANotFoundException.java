package com.infinevo.payroll.form16.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a Form 16 Part A certificate record is not found for an employee and financial year (W-36.5 §4).
 * Maps to HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class Form16PartANotFoundException extends RuntimeException {

    public Form16PartANotFoundException(String message) {
        super(message);
    }
}
