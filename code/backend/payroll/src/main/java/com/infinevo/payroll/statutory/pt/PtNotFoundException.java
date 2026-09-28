package com.infinevo.payroll.statutory.pt;

import java.io.Serial;

/**
 * Thrown when an active work location state or professional tax override is not found (W-31.2).
 * Maps to HTTP 404 Not Found.
 */
public class PtNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PtNotFoundException(String message) {
        super(message);
    }
}
