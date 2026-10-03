package com.infinevo.payroll.priorpayroll;

/**
 * Status of a bulk prior payroll import run (W-38.1 §4).
 */
public enum PriorPayrollImportStatus {
    PENDING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}
