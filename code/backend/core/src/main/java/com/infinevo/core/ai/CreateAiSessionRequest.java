package com.infinevo.core.ai;

/** Body of {@code POST /api/v1/ai/sessions} (W-114.3). The title is optional and at most 200 characters. */
public record CreateAiSessionRequest(String title) {}
