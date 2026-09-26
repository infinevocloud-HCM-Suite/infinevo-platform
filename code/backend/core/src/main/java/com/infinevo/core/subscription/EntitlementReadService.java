package com.infinevo.core.subscription;

import com.infinevo.shared.entitlement.EntitlementSnapshot;
import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
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
 * A tenant with {@code SUSPENDED} subscription is marked suspended.
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
    public EntitlementSnapshot snapshotOf(UUID tenantId) {
        if (tenantId == null) {
            return EntitlementSnapshot.empty();
        }

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByTenantId(tenantId);
        if (subscriptionOpt.isEmpty()) {
            return EntitlementSnapshot.empty();
        }

        Subscription subscription = subscriptionOpt.get();
        boolean suspended = subscription.getStatus() == SubscriptionStatus.SUSPENDED;
        if (suspended) {
            return new EntitlementSnapshot(Set.of(), Set.of(), true);
        }

        if (!subscription.getStatus().isActiveOrPastDue()) {
            return EntitlementSnapshot.empty();
        }

        List<SubscriptionModule> modules = subscriptionModuleRepository.findByTenantId(tenantId);
        Set<PlatformModule> active = modules.stream()
                .filter(m -> m.getRevokedOn() == null)
                .map(SubscriptionModule::getModule)
                .collect(Collectors.toUnmodifiableSet());
        Set<PlatformModule> revoked = modules.stream()
                .filter(m -> m.getRevokedOn() != null)
                .map(SubscriptionModule::getModule)
                .collect(Collectors.toUnmodifiableSet());

        return new EntitlementSnapshot(active, revoked, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PlatformModule> modulesOf(UUID tenantId) {
        return snapshotOf(tenantId).activeModules();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSuspended(UUID tenantId) {
        return snapshotOf(tenantId).suspended();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PlatformModule> revokedModulesOf(UUID tenantId) {
        return snapshotOf(tenantId).revokedModules();
    }
}
