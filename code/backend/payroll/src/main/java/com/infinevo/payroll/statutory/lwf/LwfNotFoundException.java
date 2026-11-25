package com.infinevo.payroll.statutory.lwf;

import java.io.Serial;

/**
 * Thrown when an LWF state setting or resource is not found (W-92).
 * Maps to HTTP 404 Not Found.
 */
public class LwfNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LwfNotFoundException(String message) {
        super(message);
    }
}
