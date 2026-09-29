package com.infinevo.core.invitation;

import java.time.Instant;
import java.util.UUID;

/**
 * Representation of an employee invitation. Never includes token or token hash.
 */
public record EmployeeInvitationResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        String email,
        InvitationStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant acceptedAt,
        Instant declinedAt,
        String declineReason,
        Instant revokedAt,
        UUID supersededById) {

    public static EmployeeInvitationResponse from(EmployeeInvitation inv) {
        return new EmployeeInvitationResponse(
                inv.getId(),
                inv.getTenantId(),
                inv.getEmployeeId(),
                inv.getEmail(),
                inv.getStatus(),
                inv.getExpiresAt(),
                inv.getCreatedAt(),
                inv.getAcceptedAt(),
                inv.getDeclinedAt(),
                inv.getDeclineReason(),
                inv.getRevokedAt(),
                inv.getSupersededById());
    }
}
