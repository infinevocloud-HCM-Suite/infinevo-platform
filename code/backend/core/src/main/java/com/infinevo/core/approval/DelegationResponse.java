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
        Instant createdAt,
        boolean revocable) {

    /** As seen by nobody in particular: {@code revocable} is false. */
    public static DelegationResponse from(ApprovalDelegation delegation) {
        return from(delegation, null);
    }

    /**
     * As seen by {@code viewerEmployeeId}. {@code revocable} is true only for the delegator of an
     * active delegation - the one caller {@code DELETE} accepts - so a screen that also lists the
     * delegations made <em>to</em> the viewer does not offer a button that answers 403.
     */
    public static DelegationResponse from(ApprovalDelegation delegation, UUID viewerEmployeeId) {
        return new DelegationResponse(
                delegation.getId(),
                delegation.getTenantId(),
                delegation.getDelegatorEmployeeId(),
                delegation.getDelegateEmployeeId(),
                delegation.getFlowTypes(),
                delegation.getEffectiveFrom(),
                delegation.getEffectiveTo(),
                delegation.isActive(),
                delegation.getCreatedAt(),
                delegation.isActive()
                        && viewerEmployeeId != null
                        && viewerEmployeeId.equals(delegation.getDelegatorEmployeeId()));
    }

    /** This delegation as {@code viewerEmployeeId} sees it. */
    public DelegationResponse seenBy(UUID viewerEmployeeId) {
        return new DelegationResponse(
                id,
                tenantId,
                delegatorEmployeeId,
                delegateEmployeeId,
                flowTypes,
                from,
                to,
                isActive,
                createdAt,
                isActive && viewerEmployeeId != null && viewerEmployeeId.equals(delegatorEmployeeId));
    }
}
