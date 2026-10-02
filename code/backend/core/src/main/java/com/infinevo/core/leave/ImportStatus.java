package com.infinevo.core.leave;

/**
 * Status of a bulk leave import run (W-16.4b, spec section 4).
 */
public enum ImportStatus {
    PENDING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}
