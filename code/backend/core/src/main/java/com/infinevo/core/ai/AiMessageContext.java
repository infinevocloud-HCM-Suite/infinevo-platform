package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The screen the user is on when they ask (W-114.13): the route path the shell sends as {@code context.path}.
 *
 * <p>It is client data. It is only matched against the caller's own navigation feed ({@link AiScreenResolver}) and then
 * dropped: it is never written into the prompt, a stored message or a log (rule 1).
 */
public final class AiMessageContext {

    /** A longer path is treated as absent. */
    static final int MAX_PATH = 200;

    private AiMessageContext() {}

    /**
     * The path from the request's {@code context}, or {@code null} when it is missing, not text, blank, does not start
     * with {@code /} or is over {@value #MAX_PATH} characters. Never a reason for a {@code 400}.
     */
    public static String pathOf(JsonNode context) {
        if (context == null || !context.isObject()) {
            return null;
        }
        JsonNode node = context.get("path");
        if (node == null || !node.isTextual()) {
            return null;
        }
        return usable(node.asText());
    }

    /** The path when usable, else {@code null}. */
    static String usable(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/") || path.length() > MAX_PATH) {
            return null;
        }
        return path;
    }
}
