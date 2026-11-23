package com.infinevo.core.subscription;

/**
 * Request payload for the AI helper switch and its monthly request cap (W-114.1).
 *
 * <p>Both values are written as given. A cap is required, and must be above 0, when {@code enabled} is true;
 * switching off keeps whatever cap is sent.
 */
public record AiSettingsUpdateRequest(boolean enabled, Integer monthlyRequestCap) {}
