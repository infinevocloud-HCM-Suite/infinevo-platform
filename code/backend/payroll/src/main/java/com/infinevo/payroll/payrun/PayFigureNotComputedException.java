package com.infinevo.payroll.payrun;

import java.io.Serial;
import java.util.UUID;

/**
 * The employee's row has no figure to explain: not computed yet, or its computation failed (the row
 * then carries the reason). Maps to {@code 409}.
 */
public class PayFigureNotComputedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayFigureNotComputedException(UUID payrunId, UUID employeeId, String computationError) {
        super("Pay run " + payrunId + " has no figure for employee " + employeeId
                + (computationError == null ? ": not computed" : ": " + computationError));
    }
}
