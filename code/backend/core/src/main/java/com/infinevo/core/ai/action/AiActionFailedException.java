package com.infinevo.core.ai.action;

/**
 * {@code 409 AI_ACTION_FAILED}: the service refused the stored payload at confirm (W-114.8, check 8). The message is
 * the service's reason, also kept in the intent's {@code result.reason}.
 */
public class AiActionFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiActionFailedException(String reason) {
        super(reason == null || reason.isBlank() ? "The action was refused." : reason);
    }
}
