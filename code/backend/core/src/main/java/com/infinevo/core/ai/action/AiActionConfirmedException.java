package com.infinevo.core.ai.action;

/** {@code 409 AI_ACTION_CONFIRMED}: cancel of an intent already carried out (W-114.8, API contract). */
public class AiActionConfirmedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiActionConfirmedException() {
        super("This action was already carried out, so it cannot be cancelled.");
    }
}
