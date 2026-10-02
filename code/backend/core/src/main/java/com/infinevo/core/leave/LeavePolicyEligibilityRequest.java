package com.infinevo.core.leave;

import java.util.UUID;

/**
 * Request payload for configuring a leave policy eligibility row (W-16.1, spec section 4 &amp; 6).
 */
public record LeavePolicyEligibilityRequest(String dimension, UUID valueId) {}
