package com.infinevo.payroll.reimbursement;

import java.io.Serial;

/**
 * Exception thrown when a reimbursement claim is not found or is inaccessible (W-35.1).
 * Maps to HTTP 404 Not Found.
 */
public class ReimbursementClaimNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReimbursementClaimNotFoundException(String message) {
        super(message);
    }
}
