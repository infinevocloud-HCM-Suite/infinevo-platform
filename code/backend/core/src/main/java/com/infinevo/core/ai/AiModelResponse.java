package com.infinevo.core.ai;

import java.util.List;
import java.util.Objects;

/**
 * What one model call answered (W-114.4): text, a list of tool calls, or a content-filter stop, with the token counts.
 *
 * @param text the answer when {@link Kind#TEXT}; an answer cut at the length limit already ends "… (answer cut short)"
 * @param toolCalls the calls when {@link Kind#TOOL_CALLS}, tool names in the platform's dotted form
 */
public record AiModelResponse(
        Kind kind, String text, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {

    /** Added to an answer the model stopped at the output limit. */
    public static final String CUT_SHORT = "… (answer cut short)";

    public AiModelResponse {
        Objects.requireNonNull(kind, "kind must not be null");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public enum Kind {
        /** A text answer. */
        TEXT,
        /** The model asks for one or more tools. */
        TOOL_CALLS,
        /** Azure's content filter stopped the question or the answer. */
        FILTERED
    }

    /** One tool the model asks for. {@code argumentsJson} is the model's text as sent, which may not be valid JSON. */
    public record ToolCall(String id, String name, String argumentsJson) {}

    public static AiModelResponse text(String text, int promptTokens, int completionTokens) {
        return new AiModelResponse(Kind.TEXT, text == null ? "" : text, List.of(), promptTokens, completionTokens);
    }

    public static AiModelResponse toolCalls(List<ToolCall> calls, int promptTokens, int completionTokens) {
        return new AiModelResponse(Kind.TOOL_CALLS, null, calls, promptTokens, completionTokens);
    }

    public static AiModelResponse filtered(int promptTokens, int completionTokens) {
        return new AiModelResponse(Kind.FILTERED, null, List.of(), promptTokens, completionTokens);
    }

    /** Test and stub convenience: text with no token counts. */
    public static AiModelResponse text(String text) {
        return text(text, 0, 0);
    }

    /** Test and stub convenience: one tool call with no token counts. */
    public static AiModelResponse toolCall(String id, String name, String argumentsJson) {
        return toolCalls(List.of(new ToolCall(id, name, argumentsJson)), 0, 0);
    }

    /** Test and stub convenience: a content-filter stop. */
    public static AiModelResponse filtered() {
        return filtered(0, 0);
    }
}
