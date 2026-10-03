package com.infinevo.payroll.priorpayroll;

import java.io.Serial;
import java.util.UUID;

/**
 * Thrown when a prior payroll month record is not found (W-38.1 §4). Maps to {@code 404 Not Found}.
 */
public class PriorPayrollNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PriorPayrollNotFoundException(UUID id) {
        super("Prior payroll record not found: " + id);
    }
}
