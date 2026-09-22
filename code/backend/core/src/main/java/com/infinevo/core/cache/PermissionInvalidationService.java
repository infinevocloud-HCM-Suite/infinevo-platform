package com.infinevo.core.cache;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for deterministic, multi-instance cache invalidations.
 *
 * <p>Ensures that when roles, actions, or user assignments mutate, the changes
 * become visible across all running container replicas immediately in 0 ms.
 */
@Service
public class PermissionInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(PermissionInvalidationService.class);

    private final PermissionCacheService permissionCacheService;

    public PermissionInvalidationService(PermissionCacheService permissionCacheService) {
        this.permissionCacheService =
                Objects.requireNonNull(permissionCacheService, "permissionCacheService must not be null");
    }

    /**
     * Invalidates cached permissions for a single user (e.g. after role reassignment).
     *
     * @param tenantId Tenant UUID
     * @param userId User identifier
     */
    public void invalidateUser(UUID tenantId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        log.info("Invalidating permissions for tenant '{}', user '{}'", tenantId, userId);
        permissionCacheService.evictUserPermissions(tenantId, userId);
    }

    /**
     * Invalidates cached permissions for all users possessing a modified role.
     *
     * @param tenantId Tenant UUID
     * @param roleId Role identifier
     * @param userIds Collection of user IDs holding the role
     */
    public void invalidateRoleUsers(UUID tenantId, String roleId, Iterable<String> userIds) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(roleId, "roleId must not be null");
        if (userIds == null) {
            return;
        }

        int count = 0;
        for (String userId : userIds) {
            permissionCacheService.evictUserPermissions(tenantId, userId);
            count++;
        }
        log.info("Invalidated permissions for {} users of role '{}' in tenant '{}'", count, roleId, tenantId);
    }

    /**
     * Flushes all cached permissions across the entire tenant.
     *
     * @param tenantId Tenant UUID
     */
    public void invalidateAllTenantPermissions(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        log.info("Invalidating all permission caches for tenant '{}'", tenantId);
        permissionCacheService.evictTenantPermissions(tenantId);
    }
}
