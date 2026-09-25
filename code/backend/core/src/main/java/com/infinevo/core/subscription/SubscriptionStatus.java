package com.infinevo.core.subscription;

/**
 * Status of a tenant's subscription (W-12.1, D-12).
 *
 * <p>Stored as a string in {@code core.subscription.status} —
 * {@code migration/src/main/resources/db/migration/core/V034__subscription.sql}.
 *
 * <p>Serves as the payment seam (D-12): downstream services and entitlement checks
 * only care about the subscription state, not the billing mechanics.
 */
public enum SubscriptionStatus {

    /** The subscription is in good standing and all entitled modules are available. */
    ACTIVE,

    /** Payment is overdue, but access is retained during the grace period. */
    PAST_DUE,

    /** Subscription is suspended; all module endpoints are locked. */
    SUSPENDED,

    /** Subscription is terminated. */
    CANCELLED;

    /**
     * True when the subscription is considered operative for module access.
     */
    public boolean isActiveOrPastDue() {
        return this == ACTIVE || this == PAST_DUE;
    }
}
