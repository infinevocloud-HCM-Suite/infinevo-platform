package com.infinevo.payroll.payrun;

import java.io.Serial;
import java.util.UUID;

/**
 * The computation could not be handed to the worker: no queue is configured, or the send failed.
 * The run is left {@code FAILED} with the reason, so it can be computed again at once. Maps to
 * {@code 503}.
 */
public class PayRunEnqueueException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayRunEnqueueException(UUID id, String reason, Throwable cause) {
        super("Pay run " + id + " could not be queued for computation: " + reason, cause);
    }
}
