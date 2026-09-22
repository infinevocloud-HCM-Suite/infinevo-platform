package com.infinevo.core.identity.dto;

import com.infinevo.core.identity.entity.UserAccount;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for UserAccount response.
 */
public record UserAccountResponse(
        UUID id,
        UUID tenantId,
        String keycloakSub,
        String email,
        String firstName,
        String lastName,
        String status,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt) {

    public static UserAccountResponse fromEntity(UserAccount entity) {
        return new UserAccountResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getKeycloakSub(),
                entity.getEmail(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getStatus(),
                entity.getLastLoginAt(),
                entity.getCreatedAt());
    }
}
