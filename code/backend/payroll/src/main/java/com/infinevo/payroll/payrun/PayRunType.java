package com.infinevo.payroll.payrun;

/**
 * The kind of run; the column has a {@code CHECK}. {@code OFF_CYCLE} (W-30.2) is created for named
 * employees, pays only the pay inputs tagged with its id, and has no structure and no loss of pay.
 */
public enum PayRunType {
    REGULAR,
    OFF_CYCLE
}
