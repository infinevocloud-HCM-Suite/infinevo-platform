package com.infinevo.payroll.tds;

/**
 * Not found failure for TDS records (W-36.1 §4).
 */
public class EmployeeTdsNotFoundException extends RuntimeException {

    public EmployeeTdsNotFoundException(String message) {
        super(message);
    }
}
