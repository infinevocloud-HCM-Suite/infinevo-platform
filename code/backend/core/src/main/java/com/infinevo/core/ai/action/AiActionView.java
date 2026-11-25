package com.infinevo.core.ai.action;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiIntentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One intent as {@code GET /api/v1/ai/actions/{intentId}} shows it (W-114.8): the card, built again from the stored
 * payload, its state and, once run, its result.
 */
public record AiActionView(
        UUID intentId,
        String actionCode,
        String summary,
        List<AiActionField> fields,
        AiIntentStatus status,
        Instant expiresAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) JsonNode result) {

    public AiActionView {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
