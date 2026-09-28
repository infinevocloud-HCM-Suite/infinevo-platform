package com.infinevo.payroll.component;

/**
 * Calculation type for a salary component (W-26.1, spec section 4).
 * Ports legacy CalculationBasis under the target name.
 */
public enum CalculationType {
    FLAT,
    PERCENTAGE
}
