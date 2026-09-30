package com.infinevo.payroll.taxdeductor;

import java.io.Serial;

/**
 * Exception thrown when tax deductor validation fails (W-36.3).
 * Maps to HTTP 400 Bad Request.
 */
public class TaxDeductorValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TaxDeductorValidationException(String message) {
        super(message);
    }
}
