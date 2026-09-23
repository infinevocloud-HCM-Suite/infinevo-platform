package com.infinevo.core.org;

import java.time.Instant;
import java.util.UUID;

/** One designation as the API returns it (W-14.1, spec section 4). See {@link DepartmentResponse}. */
public record DesignationResponse(
        UUID id, UUID tenantId, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {

    /** Repacks a persisted designation. The only way one of these is built. */
    public static DesignationResponse from(Designation designation) {
        return new DesignationResponse(
                designation.getId(),
                designation.getTenantId(),
                designation.getCode(),
                designation.getName(),
                designation.isActive(),
                designation.getCreatedAt(),
                designation.getUpdatedAt());
    }
}
