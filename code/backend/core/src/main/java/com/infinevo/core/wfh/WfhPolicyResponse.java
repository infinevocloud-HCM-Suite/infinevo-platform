package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WfhPolicyResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        BigDecimal annualDays,
        BigDecimal monthlyLimitDays,
        boolean allowHalfDay,
        int advanceNoticeDays,
        Integer maxConsecutiveDays,
        boolean requiresManagerApproval,
        boolean requiresHrApproval,
        LocalDate effectiveFrom,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static WfhPolicyResponse from(WfhPolicy entity) {
        return new WfhPolicyResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getCode(),
                entity.getName(),
                entity.getAnnualDays(),
                entity.getMonthlyLimitDays(),
                entity.isAllowHalfDay(),
                entity.getAdvanceNoticeDays(),
                entity.getMaxConsecutiveDays(),
                entity.isRequiresManagerApproval(),
                entity.isRequiresHrApproval(),
                entity.getEffectiveFrom(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
