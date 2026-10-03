package com.infinevo.payroll.taxdeductor;

import java.io.Serial;

/**
 * Exception thrown when tax deductor details are not configured for a tenant (W-36.3).
 * Maps to HTTP 404 Not Found.
 */
public class TaxDeductorNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TaxDeductorNotFoundException(String message) {
        super(message);
    }
}
