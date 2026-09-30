package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;

/**
 * The body of a {@code payrun} queue message (W-29.4 §4): which run, which attempt, and who asked —
 * the worker has no signed-in user, and the rows it writes name the officer who pressed compute.
 * A few dozen bytes, well under {@code QueueMessage.MAX_PAYLOAD_BYTES}.
 */
public record PayRunJobPayload(
        @JsonProperty("payrun_id") UUID payrunId,
        @JsonProperty("attempt") int attempt,
        @JsonProperty("requested_by") String requestedBy) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PayRunJobPayload {
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1, was " + attempt);
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise pay run job payload", e);
        }
    }

    /** @throws IllegalArgumentException if {@code json} is not a pay run job payload */
    public static PayRunJobPayload fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Pay run job payload is empty");
        }
        try {
            return MAPPER.readValue(json, PayRunJobPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not a pay run job payload: " + e.getOriginalMessage(), e);
        }
    }
}
