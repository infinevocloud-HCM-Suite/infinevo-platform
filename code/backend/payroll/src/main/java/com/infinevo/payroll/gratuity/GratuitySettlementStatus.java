package com.infinevo.payroll.gratuity;

/**
 * Lifecycle state of a recorded gratuity settlement (W-91).
 */
public enum GratuitySettlementStatus {
    DRAFT,
    APPROVED,
    PAID,
    CANCELLED
}
