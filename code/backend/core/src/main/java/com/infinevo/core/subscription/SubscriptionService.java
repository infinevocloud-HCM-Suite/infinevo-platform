package com.infinevo.core.subscription;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;
import java.util.UUID;

/**
 * Service for tenant subscription management and lifecycle operations (W-12.1).
 */
public interface SubscriptionService {

    SubscriptionResponse getSubscription(UUID tenantId);

    SubscriptionResponse updateModules(UUID tenantId, Set<PlatformModule> modules);

    SubscriptionResponse updateStatus(UUID tenantId, SubscriptionStatus status);
}
