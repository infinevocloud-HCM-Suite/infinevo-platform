package com.infinevo.core.subscription;

import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.entitlement.PlatformModule;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link SubscriptionService} (W-12.1).
 *
 * <p>Cross-tenant mutations execute through PostgreSQL {@code SECURITY DEFINER} functions
 * owned by {@code migration_user}, preserving Row-Level Security for standard queries while
 * allowing authorized platform administrators to manage tenant subscriptions.
 *
 * <p>Every actual mutation invalidates the tenant's distributed cache by calling
 * {@link PermissionCache#bumpVersion(UUID)}, synchronizing all replicas.
 */
@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionModuleRepository subscriptionModuleRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PermissionCache permissionCache;

    public SubscriptionServiceImpl(
            SubscriptionRepository subscriptionRepository,
            SubscriptionModuleRepository subscriptionModuleRepository,
            JdbcTemplate jdbcTemplate,
            PermissionCache permissionCache) {
        this.subscriptionRepository =
                Objects.requireNonNull(subscriptionRepository, "subscriptionRepository must not be null");
        this.subscriptionModuleRepository =
                Objects.requireNonNull(subscriptionModuleRepository, "subscriptionModuleRepository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.permissionCache = Objects.requireNonNull(permissionCache, "permissionCache must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Subscription subscription = subscriptionRepository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        Set<PlatformModule> activeModules =
                subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(tenantId).stream()
                        .map(SubscriptionModule::getModule)
                        .collect(Collectors.toSet());

        return toResponse(subscription, activeModules);
    }

    @Override
    @Transactional
    public SubscriptionResponse updateModules(UUID tenantId, Set<PlatformModule> modules) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Set<PlatformModule> targetSet = modules != null ? modules : Set.of();

        // 1. Verify subscription exists
        Subscription subscription = subscriptionRepository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        // 2. Read currently active modules
        Set<PlatformModule> currentSet =
                subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(tenantId).stream()
                        .map(SubscriptionModule::getModule)
                        .collect(Collectors.toSet());

        // 3. No-op check: if unchanged, do NOT bump version or call stored procedure
        if (currentSet.equals(targetSet)) {
            log.debug("No module changes for tenant {}; skipping version bump", tenantId);
            return toResponse(subscription, currentSet);
        }

        // 4. Execute stored procedure to update modules
        jdbcTemplate.execute((Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT core.set_subscription_modules(?, ?)")) {
                ps.setObject(1, tenantId);
                String[] moduleNames = targetSet.stream().map(Enum::name).toArray(String[]::new);
                Array array = conn.createArrayOf("text", moduleNames);
                ps.setArray(2, array);
                ps.execute();
                return null;
            }
        });

        // 5. Bump cache version to invalidate cached permissions and entitlements
        permissionCache.bumpVersion(tenantId);

        return toResponse(subscription, targetSet);
    }

    @Override
    @Transactional
    public SubscriptionResponse updateStatus(UUID tenantId, SubscriptionStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        // 1. Verify subscription exists
        Subscription subscription = subscriptionRepository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        // 2. No-op check: if unchanged, do NOT bump version or call stored procedure
        if (subscription.getStatus() == status) {
            log.debug("No status change for tenant {}; skipping version bump", tenantId);
            Set<PlatformModule> activeModules =
                    subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(tenantId).stream()
                            .map(SubscriptionModule::getModule)
                            .collect(Collectors.toSet());
            return toResponse(subscription, activeModules);
        }

        // 3. Execute stored procedure to update status
        jdbcTemplate.execute((Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT core.set_subscription_status(?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setString(2, status.name());
                ps.execute();
                return null;
            }
        });

        // 4. Bump cache version
        permissionCache.bumpVersion(tenantId);

        // 5. Update local entity view
        subscription.setStatus(status);

        Set<PlatformModule> activeModules =
                subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(tenantId).stream()
                        .map(SubscriptionModule::getModule)
                        .collect(Collectors.toSet());

        return toResponse(subscription, activeModules);
    }

    private static SubscriptionResponse toResponse(Subscription subscription, Set<PlatformModule> modules) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getTenantId(),
                subscription.getStatus(),
                modules,
                subscription.getStartedOn(),
                subscription.getCurrentPeriodEnd(),
                subscription.getExternalRef());
    }
}
