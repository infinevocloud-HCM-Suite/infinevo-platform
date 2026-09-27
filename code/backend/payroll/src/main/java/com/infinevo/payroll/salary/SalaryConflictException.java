package com.infinevo.payroll.salary;

import java.io.Serial;

/**
 * Exception thrown when a salary version operation conflicts with existing versions or effective dates (409 Conflict) (W-26.2).
 */
public class SalaryConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SalaryConflictException(String message) {
        super(message);
    }
}
