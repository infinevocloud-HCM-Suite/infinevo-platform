package com.infinevo.core.ai.action;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.ai.AiIntentStatus;
import java.util.UUID;

/** The answer to confirm and cancel (W-114.8, API contract): {@code { intentId, actionCode, status, result }}. */
public record AiActionOutcome(
        UUID intentId,
        String actionCode,
        AiIntentStatus status,
        @JsonInclude(JsonInclude.Include.ALWAYS) JsonNode result) {}
