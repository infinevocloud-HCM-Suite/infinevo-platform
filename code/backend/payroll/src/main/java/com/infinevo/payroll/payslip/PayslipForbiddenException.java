package com.infinevo.payroll.payslip;

import java.io.Serial;

/**
 * Thrown when an employee self-service payslip read cannot resolve an associated employee.
 * Maps to {@code 403 Forbidden}.
 */
public class PayslipForbiddenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayslipForbiddenException(String message) {
        super(message);
    }
}
