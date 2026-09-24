package com.infinevo.core.authz;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * One role and the action codes it holds, as the API returns it (W-11.1, spec section 4).
 *
 * @param system true for the seven seeded roles, which cannot be edited or deleted
 * @param actionCodes sorted, so two reads of the same role compare equal
 */
public record RoleResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        boolean system,
        List<String> actionCodes,
        Instant createdAt,
        Instant updatedAt) {

    public static RoleResponse from(Role role, Collection<String> actionCodes) {
        return new RoleResponse(
                role.getId(),
                role.getTenantId(),
                role.getCode(),
                role.getName(),
                role.isSystem(),
                actionCodes.stream().sorted().toList(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
