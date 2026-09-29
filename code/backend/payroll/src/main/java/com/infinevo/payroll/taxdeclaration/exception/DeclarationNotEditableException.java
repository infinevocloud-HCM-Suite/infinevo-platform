package com.infinevo.payroll.taxdeclaration.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an employee or officer attempts to modify or transition an investment declaration
 * that cannot be edited under current state or window rules (W-32.1).
 *
 * <p>Produces HTTP 409 Conflict with a specific reason code.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DeclarationNotEditableException extends RuntimeException {

    private final String reasonCode;

    public DeclarationNotEditableException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
