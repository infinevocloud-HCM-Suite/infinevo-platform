package com.infinevo.core.ai;

/** The AI helper is switched off for the bound tenant (W-114.1). Answers {@code 403 AI_DISABLED}. */
public class AiDisabledException extends RuntimeException {

    public AiDisabledException() {
        super("The AI helper is not switched on for your organisation.");
    }

    /** With its own message, for a refusal other than the switch (W-114.13b, act-as). */
    public AiDisabledException(String message) {
        super(message);
    }
}
