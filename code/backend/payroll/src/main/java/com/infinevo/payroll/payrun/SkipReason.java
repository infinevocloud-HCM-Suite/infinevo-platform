package com.infinevo.payroll.payrun;

/**
 * Why a considered employee is {@link InclusionStatus#SKIPPED} (W-29.1 §3). The column has no
 * {@code CHECK} on its values, so W-29.2 may add {@code NO_STATUTORY_PROFILE} without a schema change.
 */
public enum SkipReason {
    /** No salary version in force at the period's end (W-26.2). */
    NO_SALARY,
    /** No bank section on the employee record (W-13.2). */
    NO_BANK_DETAILS
}
