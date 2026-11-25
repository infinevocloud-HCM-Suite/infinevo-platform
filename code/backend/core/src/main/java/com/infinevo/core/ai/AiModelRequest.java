package com.infinevo.core.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * One model call (W-114.4): the system prompt, the conversation after it, the tools the caller may use, and the
 * limits. Tool names are the platform's dotted names; {@link AzureOpenAiModelClient} alone translates them.
 *
 * @param maxOutputTokens the answer limit, sent as {@code max_completion_tokens}
 * @param timeout how long this one call may take
 */
public record AiModelRequest(
        String systemPrompt,
        List<Message> messages,
        List<AiToolDescriptor> tools,
        int maxOutputTokens,
        Duration timeout) {

    public AiModelRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        tools = tools == null ? List.of() : List.copyOf(tools);
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be at least 1");
        }
    }

    /** Who wrote a message the model sees after the system prompt. */
    public enum Role {
        USER,
        ASSISTANT,
        TOOL
    }

    /**
     * One message. An {@code ASSISTANT} message either has {@code content} or {@code toolCalls}; a {@code TOOL}
     * message answers the call named by {@code toolCallId} and comes straight after the assistant message that asked.
     */
    public record Message(Role role, String content, List<AiModelResponse.ToolCall> toolCalls, String toolCallId) {

        public Message {
            Objects.requireNonNull(role, "role must not be null");
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public static Message user(String content) {
            return new Message(Role.USER, content, List.of(), null);
        }

        public static Message assistant(String content) {
            return new Message(Role.ASSISTANT, content, List.of(), null);
        }

        public static Message assistantToolCalls(List<AiModelResponse.ToolCall> calls) {
            return new Message(Role.ASSISTANT, null, calls, null);
        }

        public static Message tool(String toolCallId, String content) {
            return new Message(Role.TOOL, content, List.of(), Objects.requireNonNull(toolCallId, "toolCallId"));
        }
    }
}
