package com.infinevo.core.ai.action;

/** {@code 409 AI_ACTION_CANCELLED}: the intent was cancelled, so it cannot be confirmed (W-114.8, check 3). */
public class AiActionCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiActionCancelledException() {
        super("This proposal was cancelled.");
    }
}
