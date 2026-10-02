package com.infinevo.payroll.form16.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when tax deductor details are not configured for the tenant (W-36.4 §4).
 * Maps to HTTP 409 Conflict (DEDUCTOR_NOT_SET).
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "DEDUCTOR_NOT_SET")
public class DeductorNotSetException extends RuntimeException {

    public DeductorNotSetException() {
        super("Tax deductor details have not been configured for this organization");
    }

    public DeductorNotSetException(String message) {
        super(message);
    }
}
