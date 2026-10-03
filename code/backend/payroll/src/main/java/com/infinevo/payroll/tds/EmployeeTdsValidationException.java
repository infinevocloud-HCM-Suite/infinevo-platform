package com.infinevo.payroll.tds;

/**
 * Validation failure for TDS requests (W-36.1 §4).
 */
public class EmployeeTdsValidationException extends RuntimeException {

    public EmployeeTdsValidationException(String message) {
        super(message);
    }
}
