package com.infinevo.payroll.component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API response representing a {@link Reimbursement} component (W-26.1).
 */
public record ReimbursementResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String displayName,
        String reimbursementType,
        CalculationType calculationType,
        BigDecimal defaultValue,
        PercentageOf percentageOf,
        BigDecimal maxLimit,
        String carryForwardOption,
        boolean includedInCtc,
        boolean includedInSalaryStructure,
        boolean fbpComponent,
        boolean optIn,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static ReimbursementResponse from(Reimbursement reimbursement) {
        return new ReimbursementResponse(
                reimbursement.getId(),
                reimbursement.getTenantId(),
                reimbursement.getCode(),
                reimbursement.getName(),
                reimbursement.getDisplayName(),
                reimbursement.getReimbursementType(),
                reimbursement.getCalculationType(),
                reimbursement.getDefaultValue(),
                reimbursement.getPercentageOf(),
                reimbursement.getMaxLimit(),
                reimbursement.getCarryForwardOption(),
                reimbursement.isIncludedInCtc(),
                reimbursement.isIncludedInSalaryStructure(),
                reimbursement.isFbpComponent(),
                reimbursement.isOptIn(),
                reimbursement.isActive(),
                reimbursement.getCreatedAt(),
                reimbursement.getCreatedBy(),
                reimbursement.getUpdatedAt(),
                reimbursement.getUpdatedBy());
    }
}
