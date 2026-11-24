package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A question to the AI helper (W-114.4): {@code content}, 1 to 2,000 characters. {@code context} (the screen the user
 * is on) is accepted as any JSON and ignored until {@code W-114.13}; it is never a reason for a {@code 400}.
 */
public record AiMessageRequest(String content, JsonNode context) {}
