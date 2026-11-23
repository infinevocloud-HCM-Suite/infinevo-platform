package com.infinevo.core.ai;

/**
 * The model could not answer (W-114.4): an HTTP error other than a content-filter stop, a timeout, or a reply that
 * could not be read. Answers {@code 502} with {@code INTERNAL}. The message names a status or a cause, never the
 * prompt or the answer.
 */
public class AiModelException extends RuntimeException {

    public AiModelException(String message) {
        super(message);
    }

    public AiModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
