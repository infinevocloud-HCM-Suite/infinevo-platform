package com.infinevo.core.org;

import java.time.Instant;
import java.util.UUID;

/**
 * One department as the API returns it (W-14.1, spec section 4).
 *
 * <p>{@code tenantId} is included for the same reason {@code EmployeeResponse} includes it: the
 * client never chose it, so seeing which tenant answered is useful rather than redundant. It is
 * output only — {@link DepartmentRequest} has no such field.
 */
public record DepartmentResponse(
        UUID id, UUID tenantId, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {

    /** Repacks a persisted department. The only way one of these is built. */
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getTenantId(),
                department.getCode(),
                department.getName(),
                department.isActive(),
                department.getCreatedAt(),
                department.getUpdatedAt());
    }
}
