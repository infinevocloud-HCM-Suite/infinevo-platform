package com.infinevo.payroll.reimbursement;

import java.io.Serial;

/**
 * Exception thrown when reimbursement claim submission or input validation fails (W-35.1).
 * Maps to HTTP 400 Bad Request.
 */
public class ReimbursementClaimValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReimbursementClaimValidationException(String message) {
        super(message);
    }
}
