package com.infinevo.core.leave;

/**
 * Status vocabulary and allowed lifecycle transitions for leave requests (W-16.3, spec section 4 &amp; 6).
 * Aligned with 12-core-contracts.md section 5 row 4.
 */
public enum LeaveRequestStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    WITHDRAWN;

    /**
     * Checks whether this status can transition to the specified target status.
     * Only transitions defined in W-16.3 section 4 are legal.
     *
     * @param target the target status
     * @return true if legal, false otherwise
     */
    public boolean canTransitionTo(LeaveRequestStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == PENDING || target == WITHDRAWN;
            case PENDING -> target == APPROVED || target == REJECTED || target == WITHDRAWN;
            case APPROVED -> target == CANCELLED;
            case REJECTED, CANCELLED, WITHDRAWN -> false;
        };
    }
}
