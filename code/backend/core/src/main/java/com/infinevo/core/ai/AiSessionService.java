package com.infinevo.core.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The signed-in user's own AI helper chats (W-114.3). The tenant is the bound one and the user the token subject;
 * neither is ever taken from the request. A chat that is not the caller's is {@link AiSessionNotFoundException}.
 */
public interface AiSessionService {

    /**
     * Opens a chat for the caller, linked to their employee record if they have one.
     *
     * @param title optional; trimmed, blank means none, at most {@value AiSession#MAX_TITLE} characters
     * @throws IllegalArgumentException when the title is too long
     */
    AiSessionResponse create(String title);

    /** The caller's chats, newest activity first. */
    Page<AiSessionResponse> listMine(Pageable pageable);

    /** The chat's {@code USER} and {@code ASSISTANT} messages, oldest first. {@code TOOL} rows are for the model. */
    List<AiMessageResponse> messages(UUID sessionId);

    /** Deletes the chat; the database deletes its messages and intents with it. */
    void delete(UUID sessionId);
}
