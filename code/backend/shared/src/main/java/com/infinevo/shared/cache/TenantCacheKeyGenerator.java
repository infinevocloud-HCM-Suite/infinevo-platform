package com.infinevo.shared.cache;

import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates tenant-partitioned and global cache keys.
 *
 * <p>Enforces Hard Rule 7: Every tenanted cache key MUST be namespaced by {@code tenantId}
 * to strictly prevent cross-tenant cache pollution or authorization leaks.
 */
public final class TenantCacheKeyGenerator {

    private static final String PREFIX = "infinevo";
    private static final String GLOBAL_PREFIX = "infinevo:global";

    private TenantCacheKeyGenerator() {}

    /**
     * Builds a tenant-scoped cache key using the currently bound {@link TenantContext}.
     *
     * @param domain Domain category (e.g. "auth:perm", "config")
     * @param key Entity key or identifier within the domain
     * @return Cache key in format: {@code infinevo:{tenantId}:{domain}:{key}}
     * @throws IllegalStateException if no tenant is bound to the current thread
     */
    public static String tenantKey(String domain, String key) {
        UUID tenantId = TenantContext.require();
        return tenantKey(tenantId, domain, key);
    }

    /**
     * Builds a tenant-scoped cache key for an explicit tenant UUID.
     *
     * @param tenantId Explicit tenant identifier
     * @param domain Domain category
     * @param key Entity key or identifier
     * @return Cache key in format: {@code infinevo:{tenantId}:{domain}:{key}}
     */
    public static String tenantKey(UUID tenantId, String domain, String key) {
        Objects.requireNonNull(tenantId, "tenantId must not be null for tenant cache keys");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return PREFIX + ":" + tenantId + ":" + domain + ":" + key;
    }

    /**
     * Builds a pattern for invalidating all keys for a tenant within a domain.
     *
     * @param tenantId Explicit tenant identifier
     * @param domain Domain category
     * @return Cache pattern in format: {@code infinevo:{tenantId}:{domain}:*}
     */
    public static String tenantDomainPattern(UUID tenantId, String domain) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        return PREFIX + ":" + tenantId + ":" + domain + ":*";
    }

    /**
     * Builds a global (un-tenanted) cache key for static reference or statutory data.
     *
     * @param domain Reference category (e.g. "ref:tax_slab")
     * @param key Lookup key
     * @return Cache key in format: {@code infinevo:global:{domain}:{key}}
     */
    public static String globalKey(String domain, String key) {
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return GLOBAL_PREFIX + ":" + domain + ":" + key;
    }
}
