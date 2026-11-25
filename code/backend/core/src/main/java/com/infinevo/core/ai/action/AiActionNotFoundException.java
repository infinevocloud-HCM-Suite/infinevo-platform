package com.infinevo.core.ai.action;

/** {@code 404}: no such intent, or not the caller's (W-114.8, check 1). */
public class AiActionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiActionNotFoundException() {
        super("The proposed action was not found.");
    }
}
