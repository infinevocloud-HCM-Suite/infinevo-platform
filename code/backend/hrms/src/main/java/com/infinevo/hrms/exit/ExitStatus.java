package com.infinevo.hrms.exit;

/**
 * Lifecycle state of an employee exit request (W-103).
 */
public enum ExitStatus {
    SUBMITTED,
    MANAGER_APPROVED,
    CLEARANCE_IN_PROGRESS,
    READY_FOR_FNF,
    SETTLED,
    REJECTED,
    WITHDRAWN
}
