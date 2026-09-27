package com.infinevo.payroll.component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for creating or updating an {@link Earning} (W-26.1).
 */
public record EarningRequest(
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
        Boolean proRata,
        Boolean includedInCtc,
        Boolean includedInSalaryStructure,
        Boolean taxable,
        Boolean variable,
        Boolean oneTime,
        Boolean fbpComponent,
        Boolean includedInEpf,
        String epfInclusionType,
        Boolean includedInEsi,
        Boolean showInPayslip) {}
