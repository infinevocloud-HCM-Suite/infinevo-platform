package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** What a tool answered (W-114.3): {@code data} when {@code ok}, else a short {@code error} the model may read. */
public record AiToolResult(boolean ok, JsonNode data, String error) {

    public static AiToolResult success(JsonNode data) {
        return new AiToolResult(true, data, null);
    }

    public static AiToolResult failure(String error) {
        return new AiToolResult(false, null, error);
    }
}
