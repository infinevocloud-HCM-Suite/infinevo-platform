package com.infinevo.core.invitation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Representation of a company user invitation. Never includes token or token hash.
 */
public record UserInvitationResponse(
        UUID id,
        UUID tenantId,
        String email,
        InvitationStatus status,
        Instant expiresAt,
        List<UUID> roleIds,
        Instant createdAt,
        Instant acceptedAt,
        Instant declinedAt,
        String declineReason,
        Instant revokedAt,
        UUID supersededById) {

    public static UserInvitationResponse from(UserInvitation inv, List<UUID> roleIds) {
        return new UserInvitationResponse(
                inv.getId(),
                inv.getTenantId(),
                inv.getEmail(),
                inv.getStatus(),
                inv.getExpiresAt(),
                roleIds != null ? List.copyOf(roleIds) : List.of(),
                inv.getCreatedAt(),
                inv.getAcceptedAt(),
                inv.getDeclinedAt(),
                inv.getDeclineReason(),
                inv.getRevokedAt(),
                inv.getSupersededById());
    }
}
