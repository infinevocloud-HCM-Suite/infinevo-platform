package com.infinevo.payroll.statutory.lwf;

import java.io.Serial;

/**
 * Thrown when LWF setting or parameter validation fails (W-92).
 * Maps to HTTP 400 Bad Request.
 */
public class LwfValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LwfValidationException(String message) {
        super(message);
    }
}
