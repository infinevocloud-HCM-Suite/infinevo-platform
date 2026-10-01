package com.infinevo.payroll.payrun;

import java.io.Serial;

/** The run is not in a status the requested action starts from. Maps to {@code 409}. */
public class IllegalPayRunTransitionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IllegalPayRunTransitionException(PayRunStatus from, PayRunStatus to) {
        super("A pay run in status " + from + " cannot move to " + to);
    }
}
