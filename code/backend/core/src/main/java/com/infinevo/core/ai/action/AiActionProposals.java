package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiToolContext;

/**
 * The one step of an action a tool may take (W-114.8): propose it. A tool depends on this, never on
 * {@link AiActionService}, so no tool can reach confirm or cancel (rule C11).
 */
public interface AiActionProposals {

    /**
     * Stores a {@code PROPOSED} intent for the caller in the chat being answered, expiring 15 minutes from now, and
     * answers its card, built from the stored payload. Nothing else is written.
     *
     * @throws IllegalStateException when no chat message is being answered on this thread, the context is not that
     *     chat's caller, or an action was already proposed in this message
     * @throws IllegalArgumentException for an action code no executor carries out
     */
    AiProposedAction propose(AiToolContext ctx, String actionCode, JsonNode payload);
}
