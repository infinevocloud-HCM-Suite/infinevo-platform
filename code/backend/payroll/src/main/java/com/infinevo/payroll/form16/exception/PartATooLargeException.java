package com.infinevo.payroll.form16.exception;

import java.io.Serial;

/** The uploaded Part A ZIP is over its size limit (W-36.5 §4). Maps to {@code 413}. */
public class PartATooLargeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PartATooLargeException(long maxBytes) {
        super("The ZIP is larger than the " + (maxBytes / (1024 * 1024)) + " MB limit");
    }
}
