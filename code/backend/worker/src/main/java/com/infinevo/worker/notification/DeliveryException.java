package com.infinevo.worker.notification;

import java.io.Serial;

/**
 * Thrown when an email delivery provider fails (W-20.2).
 *
 * <p>Carries {@link #isTransient()} to distinguish retryable failures (e.g. network timeout,
 * rate limit 429, 5xx server errors) from permanent dead-letter failures (e.g. 4xx rejected,
 * invalid address).
 */
public class DeliveryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean transientFailure;

    public DeliveryException(String message, boolean transientFailure) {
        super(message);
        this.transientFailure = transientFailure;
    }

    public DeliveryException(String message, Throwable cause, boolean transientFailure) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public boolean isTransient() {
        return transientFailure;
    }

    public static DeliveryException transientFailure(String message) {
        return new DeliveryException(message, true);
    }

    public static DeliveryException transientFailure(String message, Throwable cause) {
        return new DeliveryException(message, cause, true);
    }

    public static DeliveryException permanentFailure(String message) {
        return new DeliveryException(message, false);
    }

    public static DeliveryException permanentFailure(String message, Throwable cause) {
        return new DeliveryException(message, cause, false);
    }
}
