package com.infinevo.payroll.component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API response representing a {@link Benefit} component (W-26.1).
 */
public record BenefitResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String displayName,
        String benefitPlan,
        String benefitCategory,
        CalculationType calculationType,
        BigDecimal defaultValue,
        PercentageOf percentageOf,
        BigDecimal maxLimit,
        boolean preTax,
        boolean oneTime,
        boolean proRata,
        boolean superannuation,
        boolean includedInCtc,
        boolean includedInSalaryStructure,
        boolean allowsEmployerContribution,
        boolean allowsEmployeeContribution,
        String taxExemptSection,
        String taxExemptionSubType,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static BenefitResponse from(Benefit benefit) {
        return new BenefitResponse(
                benefit.getId(),
                benefit.getTenantId(),
                benefit.getCode(),
                benefit.getName(),
                benefit.getDisplayName(),
                benefit.getBenefitPlan(),
                benefit.getBenefitCategory(),
                benefit.getCalculationType(),
                benefit.getDefaultValue(),
                benefit.getPercentageOf(),
                benefit.getMaxLimit(),
                benefit.isPreTax(),
                benefit.isOneTime(),
                benefit.isProRata(),
                benefit.isSuperannuation(),
                benefit.isIncludedInCtc(),
                benefit.isIncludedInSalaryStructure(),
                benefit.isAllowsEmployerContribution(),
                benefit.isAllowsEmployeeContribution(),
                benefit.getTaxExemptSection(),
                benefit.getTaxExemptionSubType(),
                benefit.isActive(),
                benefit.getCreatedAt(),
                benefit.getCreatedBy(),
                benefit.getUpdatedAt(),
                benefit.getUpdatedBy());
    }
}
