package com.infinevo.payroll.payslip;

import java.io.Serial;

/**
 * Thrown when an operation on a payslip conflicts with the pay run state.
 * Maps to {@code 409 Conflict}.
 */
public class PayslipConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayslipConflictException(String message) {
        super(message);
    }
}
