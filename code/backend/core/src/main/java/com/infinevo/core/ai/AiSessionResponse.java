package com.infinevo.core.ai;

import java.time.Instant;
import java.util.UUID;

/** One chat as the API shows it (W-114.3): {@code { id, title, createdAt, lastMessageAt }}. */
public record AiSessionResponse(UUID id, String title, Instant createdAt, Instant lastMessageAt) {

    static AiSessionResponse from(AiSession session) {
        return new AiSessionResponse(
                session.getId(), session.getTitle(), session.getCreatedAt(), session.getLastMessageAt());
    }
}
