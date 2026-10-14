package com.infinevo.core.approval;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Response payload representing an approval delegation (W-15.3). D-79: each person id carries the
 * person's display name beside it, read in one batch by the service, so the screen never prints an id.
 */
public record DelegationResponse(
        UUID id,
        UUID tenantId,
        UUID delegatorEmployeeId,
        String delegatorEmployeeName,
        UUID delegateEmployeeId,
        String delegateEmployeeName,
        String flowTypes,
        LocalDate from,
        LocalDate to,
        boolean isActive,
        Instant createdAt,
        boolean revocable) {

    /** As seen by nobody in particular: {@code revocable} is false, and no names. */
    public static DelegationResponse from(ApprovalDelegation delegation) {
        return from(delegation, null, null);
    }

    /**
     * As seen by {@code viewerEmployeeId}. {@code revocable} is true only for the delegator of an
     * active delegation - the one caller {@code DELETE} accepts - so a screen that also lists the
     * delegations made <em>to</em> the viewer does not offer a button that answers 403.
     */
    public static DelegationResponse from(ApprovalDelegation delegation, UUID viewerEmployeeId) {
        return from(delegation, viewerEmployeeId, null);
    }

    /** As {@link #from(ApprovalDelegation, UUID)}, with the delegator and delegate named from {@code names}. */
    public static DelegationResponse from(
            ApprovalDelegation delegation, UUID viewerEmployeeId, Map<UUID, String> names) {
        Map<UUID, String> safe = names != null ? names : Map.of();
        return new DelegationResponse(
                delegation.getId(),
                delegation.getTenantId(),
                delegation.getDelegatorEmployeeId(),
                nameOf(safe, delegation.getDelegatorEmployeeId()),
                delegation.getDelegateEmployeeId(),
                nameOf(safe, delegation.getDelegateEmployeeId()),
                delegation.getFlowTypes(),
                delegation.getEffectiveFrom(),
                delegation.getEffectiveTo(),
                delegation.isActive(),
                delegation.getCreatedAt(),
                delegation.isActive()
                        && viewerEmployeeId != null
                        && viewerEmployeeId.equals(delegation.getDelegatorEmployeeId()));
    }

    /** This delegation as {@code viewerEmployeeId} sees it; the names are kept. */
    public DelegationResponse seenBy(UUID viewerEmployeeId) {
        return new DelegationResponse(
                id,
                tenantId,
                delegatorEmployeeId,
                delegatorEmployeeName,
                delegateEmployeeId,
                delegateEmployeeName,
                flowTypes,
                from,
                to,
                isActive,
                createdAt,
                isActive && viewerEmployeeId != null && viewerEmployeeId.equals(delegatorEmployeeId));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
