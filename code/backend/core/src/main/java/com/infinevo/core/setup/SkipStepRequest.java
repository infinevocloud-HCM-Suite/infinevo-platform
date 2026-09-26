package com.infinevo.core.setup;

/**
 * Request payload to skip an onboarding setup step (W-24.1).
 */
public record SkipStepRequest(String reason) {}
