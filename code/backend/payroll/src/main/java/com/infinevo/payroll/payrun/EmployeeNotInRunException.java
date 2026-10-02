package com.infinevo.payroll.payrun;

import java.io.Serial;
import java.util.Collection;
import java.util.UUID;

/**
 * An off-cycle run named employees it cannot include, or an input names an employee who is not an
 * {@code INCLUDED} row of the run (W-30.2 §3). Maps to {@code 400}, naming every one of them.
 */
public class EmployeeNotInRunException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmployeeNotInRunException(String message, Collection<UUID> employeeIds) {
        super(message + ": " + employeeIds);
    }
}
