package com.infinevo.core.subscription;

/**
 * The plan a tenant is billed on (W-84.1). A label for billing only: it fixes no limit and no module.
 * {@code core.subscription.plan} is checked against these names (V188).
 */
public enum SubscriptionPlan {
    STARTER,
    GROWTH,
    ENTERPRISE
}
