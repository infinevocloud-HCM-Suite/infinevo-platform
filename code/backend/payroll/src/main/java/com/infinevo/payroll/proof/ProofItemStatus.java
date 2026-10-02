package com.infinevo.payroll.proof;

/** Review state of one proof item (W-34.1 stores it, W-34.2 changes it). */
public enum ProofItemStatus {
    PENDING,
    APPROVED,
    DISALLOWED,
    RETURNED
}
