package com.infinevo.core.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * The answer to one question (W-114.4, API contract): the stored {@code ASSISTANT} message, and {@code proposedAction},
 * always {@code null} in this part ({@code W-114.8} fills it).
 *
 * <p>Not {@link AiMessageResponse}: that is W-114.3's flat list item, with {@code proposedAction} inside the message.
 */
public record AiChatResponse(
        Message message, @JsonInclude(JsonInclude.Include.ALWAYS) AiMessageResponse.ProposedAction proposedAction) {

    /** The stored message as the API shows it. */
    public record Message(UUID id, AiMessageRole role, String content, Instant createdAt) {

        static Message of(AiMessage m) {
            return new Message(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
        }
    }
}
