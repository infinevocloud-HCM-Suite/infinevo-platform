package com.infinevo.core.ai;

/**
 * Who wrote a chat message (W-114.3) - the values of the CHECK on {@code core.ai_message.role} (V200).
 *
 * <p>{@code TOOL} rows hold a tool's answer for the model; the user's message list never shows them.
 */
public enum AiMessageRole {
    USER,
    ASSISTANT,
    TOOL
}
