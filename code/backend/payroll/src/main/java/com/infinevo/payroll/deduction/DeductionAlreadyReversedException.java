package com.infinevo.payroll.deduction;

import java.io.Serial;
import java.util.UUID;

/** The deduction is already {@code REVERSED} (W-35.2 §4). Maps to {@code 409}. */
public class DeductionAlreadyReversedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DeductionAlreadyReversedException(UUID id) {
        super("Salary deduction " + id + " is already reversed");
    }
}
