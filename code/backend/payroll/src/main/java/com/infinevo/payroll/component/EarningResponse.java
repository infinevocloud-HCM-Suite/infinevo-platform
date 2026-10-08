package com.infinevo.payroll.component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API response representing an {@link Earning} component (W-26.1).
 */
public record EarningResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String displayName,
        String earningType,
        CalculationType calculationType,
        BigDecimal defaultValue,
        PercentageOf percentageOf,
        BigDecimal maxLimit,
        String earningFrequency,
        UUID parentEarningId,
        boolean proRata,
        boolean includedInCtc,
        boolean includedInSalaryStructure,
        boolean taxable,
        boolean variable,
        boolean oneTime,
        boolean scheduledEarning,
        boolean fbpComponent,
        boolean includedInEpf,
        String epfInclusionType,
        boolean includedInEsi,
        boolean showInPayslip,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static EarningResponse from(Earning earning) {
        return new EarningResponse(
                earning.getId(),
                earning.getTenantId(),
                earning.getCode(),
                earning.getName(),
                earning.getDisplayName(),
                earning.getEarningType(),
                earning.getCalculationType(),
                earning.getDefaultValue(),
                earning.getPercentageOf(),
                earning.getMaxLimit(),
                earning.getEarningFrequency(),
                earning.getParentEarningId(),
                earning.isProRata(),
                earning.isIncludedInCtc(),
                earning.isIncludedInSalaryStructure(),
                earning.isTaxable(),
                earning.isVariable(),
                earning.isOneTime(),
                earning.isScheduledEarning(),
                earning.isFbpComponent(),
                earning.isIncludedInEpf(),
                earning.getEpfInclusionType(),
                earning.isIncludedInEsi(),
                earning.isShowInPayslip(),
                earning.isActive(),
                earning.getCreatedAt(),
                earning.getCreatedBy(),
                earning.getUpdatedAt(),
                earning.getUpdatedBy());
    }
}
