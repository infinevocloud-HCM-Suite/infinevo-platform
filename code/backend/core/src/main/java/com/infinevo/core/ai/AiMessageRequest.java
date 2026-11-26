package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A question to the AI helper (W-114.4): {@code content}, 1 to 2,000 characters. {@code context} is any JSON; from
 * W-114.13 its {@code path} names the screen the user is on ({@link AiMessageContext}). It is never a reason for a
 * {@code 400}.
 */
public record AiMessageRequest(String content, JsonNode context) {}
