package com.infinevo.payroll.deduction;

/**
 * What an ad-hoc deduction recovers (W-35.2 §13 decision 4). Legacy kept free text
 * ({@code SalaryDeduction.java:35}), which cannot be reported on; {@code OTHER} plus the row's
 * {@code reason} keeps the escape hatch. The column has a {@code CHECK} on these values.
 */
public enum DeductionType {
    ADVANCE_RECOVERY,
    LOAN_RECOVERY,
    DAMAGE,
    PENALTY,
    EXCESS_PAYMENT,
    OTHER
}
