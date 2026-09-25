package com.infinevo.core.subscription;

import java.util.UUID;

/**
 * Thrown when a subscription is requested for a tenant that has none.
 */
public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(UUID tenantId) {
        super("Subscription not found for tenant " + tenantId);
    }
}
