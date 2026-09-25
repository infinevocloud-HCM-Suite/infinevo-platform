package com.infinevo.core.subscription;

import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the {@link EntitlementSource} port (W-12.1, W-12.2).
 *
 * <p>Returns the non-revoked modules of an {@code ACTIVE} or {@code PAST_DUE} subscription.
 * A tenant without an active or past-due subscription returns an empty set, failing closed.
 */
@Service
public class EntitlementReadService implements EntitlementSource {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionModuleRepository subscriptionModuleRepository;

    public EntitlementReadService(
            SubscriptionRepository subscriptionRepository, SubscriptionModuleRepository subscriptionModuleRepository) {
        this.subscriptionRepository =
                Objects.requireNonNull(subscriptionRepository, "subscriptionRepository must not be null");
        this.subscriptionModuleRepository =
                Objects.requireNonNull(subscriptionModuleRepository, "subscriptionModuleRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PlatformModule> modulesOf(UUID tenantId) {
        if (tenantId == null) {
            return Set.of();
        }

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByTenantId(tenantId);
        if (subscriptionOpt.isEmpty()) {
            return Set.of();
        }

        Subscription subscription = subscriptionOpt.get();
        if (!subscription.getStatus().isActiveOrPastDue()) {
            return Set.of();
        }

        return subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(tenantId).stream()
                .map(SubscriptionModule::getModule)
                .collect(Collectors.toUnmodifiableSet());
    }
}
