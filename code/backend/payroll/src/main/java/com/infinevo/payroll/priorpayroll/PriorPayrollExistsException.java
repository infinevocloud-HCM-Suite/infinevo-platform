package com.infinevo.payroll.priorpayroll;

import java.io.Serial;
import java.time.YearMonth;

/**
 * Thrown when attempting to create a regular pay run for a period that already has imported
 * prior payroll rows (W-38.1 §3 &amp; §4). Maps to {@code 409 Conflict}.
 */
public class PriorPayrollExistsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PriorPayrollExistsException(YearMonth period) {
        super("Prior payroll records exist for " + period + "; cannot create a pay run for an imported month");
    }

    public PriorPayrollExistsException(String period) {
        super("Prior payroll records exist for " + period + "; cannot create a pay run for an imported month");
    }
}
