package com.infinevo.core.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response representation of a leave type, optionally including its current effective policy (W-16.1).
 */
public record LeaveTypeResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        boolean isPaid,
        LeaveUnit unit,
        boolean allowHalfDay,
        LocalDate validFrom,
        LocalDate validTo,
        boolean isActive,
        LeavePolicyResponse policy,
        Instant createdAt,
        Instant updatedAt) {

    public static LeaveTypeResponse from(LeaveType type, LeavePolicyResponse policy) {
        if (type == null) {
            return null;
        }
        return new LeaveTypeResponse(
                type.getId(),
                type.getTenantId(),
                type.getCode(),
                type.getName(),
                type.isPaid(),
                type.getUnit(),
                type.isAllowHalfDay(),
                type.getValidFrom(),
                type.getValidTo(),
                type.isActive(),
                policy,
                type.getCreatedAt(),
                type.getUpdatedAt());
    }
}
