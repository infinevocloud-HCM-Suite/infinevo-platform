package com.infinevo.core.leave;

import java.util.UUID;

/**
 * Response representation of leave policy eligibility (W-16.1).
 */
public record LeavePolicyEligibilityResponse(UUID id, String dimension, UUID valueId) {

    public static LeavePolicyEligibilityResponse from(LeavePolicyEligibility entity) {
        if (entity == null) {
            return null;
        }
        return new LeavePolicyEligibilityResponse(entity.getId(), entity.getDimension(), entity.getValueId());
    }
}
