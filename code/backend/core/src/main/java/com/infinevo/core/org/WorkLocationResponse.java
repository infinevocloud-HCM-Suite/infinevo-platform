package com.infinevo.core.org;

import java.time.Instant;
import java.util.UUID;

/**
 * One work location as the API returns it (W-14.1, spec section 4).
 *
 * <p>The address and {@code filingAddress} are the difference from the other two masters — see
 * {@link WorkLocation}.
 */
public record WorkLocationResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String stateCode,
        String zipCode,
        String countryCode,
        boolean filingAddress,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    /** Repacks a persisted work location. The only way one of these is built. */
    public static WorkLocationResponse from(WorkLocation location) {
        return new WorkLocationResponse(
                location.getId(),
                location.getTenantId(),
                location.getCode(),
                location.getName(),
                location.getAddressLine1(),
                location.getAddressLine2(),
                location.getCity(),
                location.getState(),
                location.getStateCode(),
                location.getZipCode(),
                location.getCountryCode(),
                location.isFilingAddress(),
                location.isActive(),
                location.getCreatedAt(),
                location.getUpdatedAt());
    }
}
