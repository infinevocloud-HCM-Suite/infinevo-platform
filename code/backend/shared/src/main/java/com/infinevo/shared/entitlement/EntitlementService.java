package com.infinevo.shared.entitlement;

import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does the bound tenant hold entitlement to this platform module? (W-12.2, spec section 3).
 *
 * <p><strong>Fails closed.</strong> Each of these refuses: no tenant bound; no {@link PermissionCache};
 * no {@link EntitlementSource} bean; or any error reading from Redis or the source.
 *
 * <p><strong>Refusals:</strong>
 * <ul>
 *   <li>If subscription is suspended: {@code 403 TENANT_SUSPENDED} (decision 1).
 *   <li>If module is not held: {@code 403 MODULE_NOT_ENTITLED}.
 *   <li>If module is revoked: safe read-only methods (GET, HEAD, OPTIONS) pass under
 *       {@link RequiresModule.Mode#READ_WRITE} for statutory retention, while state-mutating
 *       requests refuse with {@code MODULE_NOT_ENTITLED} (decision 2).
 * </ul>
 */
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    private final Supplier<PermissionCache> cache;
    private final Supplier<EntitlementSource> entitlementSource;

    public EntitlementService(Supplier<PermissionCache> cache, Supplier<EntitlementSource> entitlementSource) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.entitlementSource = Objects.requireNonNull(entitlementSource, "entitlementSource must not be null");
    }

    /**
     * Checks whether the current bound tenant actively holds {@code module}.
     *
     * @param module the module to check
     * @return true only if actively held and not suspended; false otherwise
     */
    public boolean holds(PlatformModule module) {
        if (module == null) {
            return false;
        }
        Optional<UUID> tenantId = TenantContext.current();
        if (tenantId.isEmpty()) {
            return false;
        }
        try {
            EntitlementSnapshot snapshot = loadSnapshot(tenantId.get());
            return snapshot != null && !snapshot.suspended() && snapshot.holds(module);
        } catch (RuntimeException e) {
            log.warn("Entitlement holds check failed closed for tenant {}: {}", tenantId.get(), e.getMessage());
            return false;
        }
    }

    /**
     * Requires the bound tenant to hold {@code module} in read-write mode.
     *
     * @throws EntitlementDeniedException if not entitled or subscription suspended
     */
    public void require(PlatformModule module) {
        require(module, RequiresModule.Mode.READ_WRITE, false);
    }

    /**
     * Requires the bound tenant to hold {@code module}, indicating if this is a read-only request.
     *
     * @throws EntitlementDeniedException if not entitled or subscription suspended
     */
    public void require(PlatformModule module, boolean isReadOnly) {
        require(module, RequiresModule.Mode.READ_WRITE, isReadOnly);
    }

    /**
     * Requires the bound tenant to hold {@code module} under the given enforcement mode.
     *
     * @param module the required platform module
     * @param mode the enforcement mode
     * @param isReadOnly whether the request method is safe (GET, HEAD, OPTIONS)
     * @throws EntitlementDeniedException if not entitled or subscription suspended
     */
    public void require(PlatformModule module, RequiresModule.Mode mode, boolean isReadOnly) {
        if (module == null) {
            log.warn("Entitlement refused: no module given");
            throw new EntitlementDeniedException(ApiError.MODULE_NOT_ENTITLED, null);
        }

        Optional<UUID> tenantId = TenantContext.current();
        if (tenantId.isEmpty()) {
            log.warn("Entitlement refused for {}: no tenant bound to this request", module);
            throw new EntitlementDeniedException(ApiError.MODULE_NOT_ENTITLED, module);
        }

        EntitlementSnapshot snapshot;
        try {
            snapshot = loadSnapshot(tenantId.get());
        } catch (RuntimeException e) {
            log.warn("Entitlement check failed closed for tenant {}: {}", tenantId.get(), e.getMessage());
            throw new EntitlementDeniedException(ApiError.MODULE_NOT_ENTITLED, module);
        }

        if (snapshot == null) {
            throw new EntitlementDeniedException(ApiError.MODULE_NOT_ENTITLED, module);
        }

        if (snapshot.suspended()) {
            log.info("Entitlement refused for {}: tenant {} subscription is suspended", module, tenantId.get());
            throw new EntitlementDeniedException(ApiError.TENANT_SUSPENDED, module);
        }

        if (snapshot.holds(module)) {
            return;
        }

        if (mode == RequiresModule.Mode.READ_WRITE && isReadOnly && snapshot.isRevoked(module)) {
            log.debug("Permitting read-only access to revoked module {} for tenant {}", module, tenantId.get());
            return;
        }

        log.info("Entitlement refused for {}: tenant {} does not hold it", module, tenantId.get());
        throw new EntitlementDeniedException(ApiError.MODULE_NOT_ENTITLED, module);
    }

    private EntitlementSnapshot loadSnapshot(UUID tenantId) {
        PermissionCache permissionCache = cache.get();
        if (permissionCache == null) {
            log.warn("Entitlement check refused: no PermissionCache is configured");
            return null;
        }
        EntitlementSource source = entitlementSource.get();
        if (source == null) {
            log.warn("Entitlement check refused: no EntitlementSource bean is configured");
            return null;
        }
        return permissionCache.entitlementOf(tenantId, () -> source.snapshotOf(tenantId));
    }
}
