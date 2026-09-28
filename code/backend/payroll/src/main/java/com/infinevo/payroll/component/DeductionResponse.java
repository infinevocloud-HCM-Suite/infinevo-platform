package com.infinevo.payroll.component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API response representing a {@link Deduction} component (W-26.1).
 */
public record DeductionResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String displayName,
        String deductionType,
        CalculationType calculationType,
        BigDecimal defaultValue,
        PercentageOf percentageOf,
        BigDecimal maxLimit,
        boolean recurring,
        boolean preTax,
        String emiType,
        BigDecimal perquisiteInterestRate,
        BigDecimal emiInterestRate,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static DeductionResponse from(Deduction deduction) {
        return new DeductionResponse(
                deduction.getId(),
                deduction.getTenantId(),
                deduction.getCode(),
                deduction.getName(),
                deduction.getDisplayName(),
                deduction.getDeductionType(),
                deduction.getCalculationType(),
                deduction.getDefaultValue(),
                deduction.getPercentageOf(),
                deduction.getMaxLimit(),
                deduction.isRecurring(),
                deduction.isPreTax(),
                deduction.getEmiType(),
                deduction.getPerquisiteInterestRate(),
                deduction.getEmiInterestRate(),
                deduction.isActive(),
                deduction.getCreatedAt(),
                deduction.getCreatedBy(),
                deduction.getUpdatedAt(),
                deduction.getUpdatedBy());
    }
}
