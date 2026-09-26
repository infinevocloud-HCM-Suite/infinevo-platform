package com.infinevo.core.subscription;

/**
 * Request payload for updating a tenant's subscription status (W-12.1).
 */
public record StatusUpdateRequest(SubscriptionStatus status) {}
