package com.infinevo.payroll.component;

import java.math.BigDecimal;

/**
 * Request payload for creating or updating a {@link Reimbursement} (W-26.1).
 */
public record ReimbursementRequest(
        String code,
        String name,
        String displayName,
        String reimbursementType,
        CalculationType calculationType,
        BigDecimal defaultValue,
        PercentageOf percentageOf,
        BigDecimal maxLimit,
        String carryForwardOption,
        Boolean includedInCtc,
        Boolean includedInSalaryStructure,
        Boolean fbpComponent,
        Boolean optIn) {}
