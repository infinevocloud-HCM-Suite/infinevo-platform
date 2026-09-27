package com.infinevo.payroll.component;

import java.math.BigDecimal;

/**
 * Request payload for creating or updating a {@link Benefit} (W-26.1).
 */
public record BenefitRequest(
        String code,
        String name,
        String displayName,
        String benefitPlan,
        String benefitCategory,
        CalculationType calculationType,
        BigDecimal defaultValue,
        PercentageOf percentageOf,
        BigDecimal maxLimit,
        Boolean preTax,
        Boolean oneTime,
        Boolean proRata,
        Boolean superannuation,
        Boolean includedInCtc,
        Boolean includedInSalaryStructure,
        Boolean allowsEmployerContribution,
        Boolean allowsEmployeeContribution,
        String taxExemptSection,
        String taxExemptionSubType) {}
