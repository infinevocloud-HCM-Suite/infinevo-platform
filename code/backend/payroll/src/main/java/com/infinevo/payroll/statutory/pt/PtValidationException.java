package com.infinevo.payroll.statutory.pt;

import java.io.Serial;

/**
 * Thrown when professional tax override slab or parameter validation fails (W-31.2).
 * Maps to HTTP 400 Bad Request.
 */
public class PtValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PtValidationException(String message) {
        super(message);
    }
}
