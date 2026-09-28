package com.infinevo.core.approval;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload representing an approval delegation (W-15.3).
 */
public record DelegationResponse(
        UUID id,
        UUID tenantId,
        UUID delegatorEmployeeId,
        UUID delegateEmployeeId,
        String flowTypes,
        LocalDate from,
        LocalDate to,
        boolean isActive,
        Instant createdAt) {

    public static DelegationResponse from(ApprovalDelegation delegation) {
        return new DelegationResponse(
                delegation.getId(),
                delegation.getTenantId(),
                delegation.getDelegatorEmployeeId(),
                delegation.getDelegateEmployeeId(),
                delegation.getFlowTypes(),
                delegation.getEffectiveFrom(),
                delegation.getEffectiveTo(),
                delegation.isActive(),
                delegation.getCreatedAt());
    }
}
