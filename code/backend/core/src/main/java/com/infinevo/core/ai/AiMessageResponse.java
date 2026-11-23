package com.infinevo.core.ai;

import java.time.Instant;
import java.util.UUID;

/**
 * One message as the API shows it (W-114.3). {@code proposedAction} is {@code { intentId }} on an assistant message
 * that proposed an action, else {@code null}; the card reads the intent's state from {@code W-114.8}'s endpoint.
 */
public record AiMessageResponse(
        UUID id, AiMessageRole role, String content, Instant createdAt, ProposedAction proposedAction) {

    /** The action an assistant message proposed. */
    public record ProposedAction(UUID intentId) {}
}
