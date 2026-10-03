package com.infinevo.payroll.form16.exception;

import java.io.Serial;

/** No Form 16 Part A on file for the caller and year (W-36.5 §4). Maps to {@code 404}. */
public class PartANotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PartANotFoundException(String message) {
        super(message);
    }
}
