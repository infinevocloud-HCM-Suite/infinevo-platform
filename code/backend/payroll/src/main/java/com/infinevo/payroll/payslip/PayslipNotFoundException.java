package com.infinevo.payroll.payslip;

import java.io.Serial;

/**
 * Thrown when a payslip or related record cannot be found, or is not in an accessible state.
 * Maps to {@code 404 Not Found}.
 */
public class PayslipNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayslipNotFoundException(String message) {
        super(message);
    }
}
