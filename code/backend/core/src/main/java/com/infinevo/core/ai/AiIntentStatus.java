package com.infinevo.core.ai;

/** The state of a proposed action (W-114.3) - the values of the CHECK on {@code core.ai_action_intent.status} (V200). */
public enum AiIntentStatus {
    PROPOSED,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    FAILED
}
