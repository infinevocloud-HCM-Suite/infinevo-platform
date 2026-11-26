package com.infinevo.core.ai;

import java.util.UUID;

/**
 * Answers one question in one of the caller's chats (W-114.4, spec "Flow"). The tenant is the bound one and the user
 * the token subject; neither is ever taken from the request.
 */
public interface AiChatService {

    /** The longest question accepted. */
    int MAX_CONTENT = 2000;

    /**
     * Stores the question, asks the model - running the tools it asks for, as the caller - and stores and returns the
     * answer.
     *
     * @throws IllegalArgumentException when {@code content} is empty or over {@value #MAX_CONTENT} characters
     * @throws AiSessionNotFoundException when the chat is not the caller's
     * @throws AiDisabledException when the helper is off for the customer
     * @throws AiCapReachedException when the month's cap is reached
     * @throws AiModelException when the model fails or the request runs out of time; the question stays stored and
     *     counted
     */
    default AiChatResponse send(UUID sessionId, String content) {
        return send(sessionId, content, null);
    }

    /**
     * The same, with the route path of the screen the user is on (W-114.13), or {@code null}. The path is matched
     * against the caller's own feed only and is never stored; a path the caller may not open counts as none.
     */
    AiChatResponse send(UUID sessionId, String content, String screenPath);
}
