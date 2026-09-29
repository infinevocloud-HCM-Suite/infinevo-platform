package com.infinevo.payroll.taxdeclaration.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when window dates or settings violate validation rules (W-32.1).
 *
 * <p>Produces HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class WindowValidationException extends RuntimeException {

    public WindowValidationException(String message) {
        super(message);
    }
}
