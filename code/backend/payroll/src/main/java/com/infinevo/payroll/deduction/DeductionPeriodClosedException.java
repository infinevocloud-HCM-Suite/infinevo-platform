package com.infinevo.payroll.deduction;

import java.io.Serial;

/**
 * A batch line names a CLOSED month - its run was approved or paid (D-144). Maps to {@code 409}, with
 * the zero-based {@link #line} under {@code fieldErrors.line} the way a {@code 400} names it, so the
 * grid can mark the row. Nothing of the batch was written. The message names the month to use.
 */
public class DeductionPeriodClosedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int line;

    public DeductionPeriodClosedException(int line, String message) {
        super("Line " + line + ": " + message);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
