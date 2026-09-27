package com.infinevo.payroll.component;

import java.math.BigDecimal;

/**
 * Request payload for creating or updating a {@link Deduction} (W-26.1).
 */
public record DeductionRequest(
        String code,
        String name,
        String displayName,
        String deductionType,
        CalculationType calculationType,
        BigDecimal defaultValue,
        PercentageOf percentageOf,
        BigDecimal maxLimit,
        Boolean recurring,
        Boolean preTax,
        String emiType,
        BigDecimal perquisiteInterestRate,
        BigDecimal emiInterestRate) {}
