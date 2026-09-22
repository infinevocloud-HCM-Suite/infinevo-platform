package com.infinevo.core.cache;

import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.cache.TenantCacheKeyGenerator;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Domain cache adapter for authorization action permissions.
 *
 * <p>Replaces legacy in-memory {@code ConcurrentHashMap} (DEBT-020).
 * Caches permission action sets partitioned strictly by tenant UUID (Hard Rule 7).
 */
@Service
public class PermissionCacheService {

    private static final String DOMAIN = "auth:perm";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final CacheService cacheService;

    public PermissionCacheService(CacheService cacheService) {
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService must not be null");
    }

    /**
     * Retrieves cached action permissions for a user within a tenant.
     *
     * @param tenantId Tenant UUID
     * @param userId User identifier
     * @return Cached action permissions or empty if not cached
     */
    @SuppressWarnings("unchecked")
    public Optional<Set<String>> getUserPermissions(UUID tenantId, String userId) {
        String key = TenantCacheKeyGenerator.tenantKey(tenantId, DOMAIN, userId);
        return (Optional<Set<String>>) (Optional<?>) cacheService.get(key, Set.class);
    }

    /**
     * Stores action permissions for a user within a tenant.
     *
     * @param tenantId Tenant UUID
     * @param userId User identifier
     * @param permissions Set of allowed action keys
     */
    public void putUserPermissions(UUID tenantId, String userId, Set<String> permissions) {
        String key = TenantCacheKeyGenerator.tenantKey(tenantId, DOMAIN, userId);
        cacheService.put(key, permissions, DEFAULT_TTL);
    }

    /**
     * Evicts cached permissions for a specific user within a tenant.
     *
     * @param tenantId Tenant UUID
     * @param userId User identifier
     */
    public void evictUserPermissions(UUID tenantId, String userId) {
        String key = TenantCacheKeyGenerator.tenantKey(tenantId, DOMAIN, userId);
        cacheService.evict(key);
    }

    /**
     * Evicts all cached permissions for all users in a tenant.
     *
     * @param tenantId Tenant UUID
     */
    public void evictTenantPermissions(UUID tenantId) {
        String pattern = TenantCacheKeyGenerator.tenantDomainPattern(tenantId, DOMAIN);
        cacheService.evictPattern(pattern);
    }
}
