package com.infinevo.payroll.payrun;

import java.io.Serial;
import java.util.UUID;

/**
 * A worker was handed an attempt the run no longer expects: the run left {@code COMPUTING}, or a newer
 * attempt started after this one was judged abandoned (W-29.4 §3). The message is dropped, never retried.
 */
public class SupersededPayRunJobException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SupersededPayRunJobException(UUID id, int attempt, PayRunStatus status, int currentAttempt) {
        super("Pay run " + id + " attempt " + attempt + " is superseded: the run is " + status + " at attempt "
                + currentAttempt);
    }
}
