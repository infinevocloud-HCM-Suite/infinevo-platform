package com.infinevo.payroll.payrun;

import java.io.Serial;
import java.util.UUID;

/**
 * {@code POST /compute} on a run the worker is still computing, within the stale window (W-29.4 §3).
 * Maps to {@code 409}.
 */
public class PayRunComputeInProgressException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayRunComputeInProgressException(UUID id, long staleAfterMinutes) {
        super("Pay run " + id + " is being computed; it can be restarted after " + staleAfterMinutes
                + " minutes without progress");
    }
}
