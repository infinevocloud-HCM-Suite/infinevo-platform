package com.infinevo.payroll.statutory.lwf;

import java.math.BigDecimal;

/**
 * Result of Labour Welfare Fund calculation for an employee in a pay period (W-92).
 */
public record LwfCalculationResult(BigDecimal employeeAmount, BigDecimal employerAmount) {

    public static final LwfCalculationResult ZERO = new LwfCalculationResult(BigDecimal.ZERO, BigDecimal.ZERO);

    public LwfCalculationResult {
        employeeAmount = employeeAmount != null ? employeeAmount : BigDecimal.ZERO;
        employerAmount = employerAmount != null ? employerAmount : BigDecimal.ZERO;
    }

    public static LwfCalculationResult zero() {
        return ZERO;
    }

    public static LwfCalculationResult of(BigDecimal employeeAmount, BigDecimal employerAmount) {
        return new LwfCalculationResult(employeeAmount, employerAmount);
    }

    public boolean hasContribution() {
        return employeeAmount.signum() > 0 || employerAmount.signum() > 0;
    }
}
