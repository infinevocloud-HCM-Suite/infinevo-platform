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

    /**
     * Sets the tenant's plan, support plan and employee limit (W-84.1). Each value is written as given; {@code null}
     * clears it, and a {@code null} limit means unlimited. Refused for the platform tenant.
     */
    SubscriptionResponse updatePlan(UUID tenantId, PlanUpdateRequest request);

    /**
     * Sets the tenant's AI helper switch and monthly request cap (W-114.1). Both are written as given. Refused for
     * the platform tenant; refused when the switch is on with no cap, or when a cap below 1 is given.
     */
    SubscriptionResponse updateAi(UUID tenantId, AiSettingsUpdateRequest request);
}
