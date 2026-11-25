package com.infinevo.core.ai.action;

/** {@code 410 AI_ACTION_EXPIRED}: the intent is past its time and was not carried out (W-114.8, check 4). */
public class AiActionExpiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiActionExpiredException() {
        super("This proposal has expired and was not carried out. Please ask again.");
    }
}
