package com.infinevo.core.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The card of an action the helper proposed (W-114.8): the answer's {@code proposedAction}. {@code summary} and
 * {@code fields} are built by the server from the stored payload, never from text the model wrote (rule C1).
 */
public record AiProposedAction(
        UUID intentId,
        String actionCode,
        String summary,
        List<AiActionField> fields,
        AiIntentStatus status,
        Instant expiresAt) {

    public AiProposedAction {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
