package com.infinevo.payroll.deduction;

import java.io.Serial;
import java.util.UUID;

/** No such deduction in the bound tenant — another tenant's is reported the same way. Maps to {@code 404}. */
public class EmployeeDeductionNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmployeeDeductionNotFoundException(UUID id) {
        super("No salary deduction " + id + " in this tenant");
    }
}
