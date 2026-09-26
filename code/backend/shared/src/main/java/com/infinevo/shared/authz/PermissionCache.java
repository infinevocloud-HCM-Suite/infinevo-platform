package com.infinevo.shared.authz;

import com.infinevo.shared.cache.CacheOperationException;
import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.cache.TenantCacheKeyGenerator;
import com.infinevo.shared.entitlement.EntitlementSnapshot;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Users' action sets in the shared cache, invalidated by a per-tenant version (W-11.2, spec section
 * 4; W-12.2).
 *
 * <p><strong>A thin wrapper over {@link CacheService}.</strong> It opens no connection and owns no
 * configuration; it adds two key shapes and the rules for reading them. The frozen cache it replaces
 * was a map inside the process
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/auth/AuthzServiceImpl.java:29}),
 * invalidated by hand from a controller ({@code RoleActionController.java:94}) — which reached only the
 * replica that served the call.
 *
 * <p><strong>Keys.</strong>
 *
 * <ul>
 *   <li>{@code infinevo:{tenant}:authz:version} — the tenant's permission version, an opaque random
 *       token. No expiry: it lives until it is replaced.
 *   <li>{@code infinevo:{tenant}:authz:perm:{user}:{version}} — one user's action codes as a JSON
 *       array, for {@link #PERMISSION_TTL}. Values are action codes only, never employee data (spec
 *       section 9).
 *   <li>{@code infinevo:{tenant}:authz:entitlement:{version}} — tenant module entitlement snapshot.
 * </ul>
 *
 * <p><strong>Invalidation is by version, not by eviction.</strong> {@link #bumpVersion} replaces the
 * tenant's token, so every replica's next lookup builds a key nobody has written, misses, and reloads.
 * No replica is told anything. The old entries are never read again and age out on their TTL.
 *
 * <p><strong>Why a random token and not a counter.</strong> {@link CacheService} has no atomic
 * increment, and a read-increment-write from two replicas at once could land both on the same number,
 * so one of the two changes would never invalidate. A fresh random token is always new. The same
 * property makes a missing version safe to create: whatever token gets written has never keyed a
 * permission set, so it cannot resurrect a stale one.
 *
 * <p><strong>Why a missing version is created and read back.</strong> {@code RedisCacheService.get}
 * answers "empty" both for a missing key and for an unreachable Redis — it fails open by design. Were a
 * missing version read as {@code 0}, a flaky read of the version followed by a good read of an old
 * {@code :0} entry would serve a permission set from before the last change. So a missing version is
 * written and read again; if that read is still empty the cache is unreachable and the lookup throws
 * {@link CacheOperationException}, which {@link PermissionService} turns into a refusal.
 */
public class PermissionCache {

    private static final Logger log = LoggerFactory.getLogger(PermissionCache.class);

    /**
     * How long one user's action set is kept. Bounds how stale a set can be if a write path ever
     * forgets to bump — the backstop, not the mechanism — and how long superseded entries linger.
     */
    public static final Duration PERMISSION_TTL = Duration.ofMinutes(10);

    static final String DOMAIN = "authz";
    static final String VERSION_KEY = "version";
    static final String PERMISSION_DOMAIN = "authz:perm";
    static final String ENTITLEMENT_DOMAIN = "authz:entitlement";

    private final Supplier<CacheService> cacheService;

    /** For a context that is known to have a cache. */
    public PermissionCache(CacheService cacheService) {
        Objects.requireNonNull(cacheService, "cacheService must not be null");
        this.cacheService = () -> cacheService;
    }

    /**
     * For the auto-configuration: the cache is looked up per call and may be absent
     * ({@code infinevo.cache.enabled=false}, or a slice test that does not scan it).
     */
    public PermissionCache(Supplier<CacheService> cacheService) {
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService must not be null");
    }

    /**
     * The user's action set in the tenant: from the cache under the current version, or from
     * {@code loader} on a miss, which is then cached.
     *
     * <p>An empty set is cached like any other, as the frozen code did
     * ({@code AuthzServiceImpl.java:53-55}); otherwise every request from a user who holds nothing
     * would reach the database. A loader that throws caches nothing.
     *
     * @param userId the Keycloak subject. Unique with the tenant, so it names one
     *     {@code core.user_account} row without a database read on a hit
     * @throws CacheOperationException if there is no cache or the tenant's version can be neither read
     *     nor established — the cache is unreachable
     */
    public Set<String> actionsOf(UUID tenantId, UUID userId, Supplier<Set<String>> loader) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(loader, "loader must not be null");
        CacheService cache = requireCache();

        String key = permissionKey(tenantId, userId, currentVersion(cache, tenantId));
        Optional<String[]> cached = cache.get(key, String[].class);
        if (cached.isPresent()) {
            return Set.copyOf(Arrays.asList(cached.get()));
        }

        Set<String> loaded = loader.get();
        Set<String> fresh = loaded == null ? Set.of() : Set.copyOf(loaded);
        cache.put(key, fresh.stream().sorted().toArray(String[]::new), PERMISSION_TTL);
        return fresh;
    }

    /**
     * The tenant's module entitlement snapshot: from the cache under the current version, or from
     * {@code loader} on a miss, which is then cached (W-12.2).
     *
     * @param tenantId the tenant id
     * @param loader fallback supplier to load from the port on miss
     * @return the cached or freshly loaded entitlement snapshot
     * @throws CacheOperationException if the cache is unreachable
     */
    public EntitlementSnapshot entitlementOf(UUID tenantId, Supplier<EntitlementSnapshot> loader) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(loader, "loader must not be null");
        CacheService cache = requireCache();

        String key = entitlementKey(tenantId, currentVersion(cache, tenantId));
        Optional<EntitlementSnapshot> cached = cache.get(key, EntitlementSnapshot.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        EntitlementSnapshot loaded = loader.get();
        EntitlementSnapshot fresh = loaded == null ? EntitlementSnapshot.empty() : loaded;
        cache.put(key, fresh, PERMISSION_TTL);
        return fresh;
    }

    /**
     * Invalidates every cached action set in the tenant, on every replica.
     *
     * <p>Public because {@code core}'s {@code RoleServiceImpl} calls it after every role, action-set
     * or grant change — in the service layer, not a controller, which is where the frozen design put
     * its invalidation and why it was missed (spec section 9).
     *
     * <p>Evict first, then write a fresh token. The eviction is what makes a failure loud:
     * {@code CacheService.evict} throws {@link CacheOperationException} when Redis is unreachable,
     * where {@code put} only logs. If the eviction succeeds and the write is lost, the version is
     * simply missing and the next reader creates a fresh one — invalidated either way.
     *
     * <p>A context with no cache has nothing cached, so there is nothing to invalidate and this does
     * nothing.
     *
     * @throws CacheOperationException if the old version could not be removed
     */
    public void bumpVersion(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        CacheService cache = cacheService.get();
        if (cache == null) {
            log.debug("No cache configured; nothing to invalidate for tenant {}", tenantId);
            return;
        }
        String key = versionKey(tenantId);
        cache.evict(key);
        cache.put(key, newVersion(), null);
        log.info("Permission version bumped for tenant {}", tenantId);
    }

    /** The key of the tenant's version. Package-private for the tests. */
    static String versionKey(UUID tenantId) {
        return TenantCacheKeyGenerator.tenantKey(tenantId, DOMAIN, VERSION_KEY);
    }

    /** The key of one user's set under one version. Package-private for the tests. */
    static String permissionKey(UUID tenantId, UUID userId, String version) {
        return TenantCacheKeyGenerator.tenantKey(tenantId, PERMISSION_DOMAIN, userId + ":" + version);
    }

    /** The key of tenant's entitlement snapshot under one version. Package-private for the tests. */
    static String entitlementKey(UUID tenantId, String version) {
        return TenantCacheKeyGenerator.tenantKey(tenantId, ENTITLEMENT_DOMAIN, version);
    }

    private CacheService requireCache() {
        CacheService cache = cacheService.get();
        if (cache == null) {
            throw new CacheOperationException("No CacheService is configured; permission sets cannot be cached");
        }
        return cache;
    }

    private static String currentVersion(CacheService cache, UUID tenantId) {
        String key = versionKey(tenantId);
        Optional<String> version = cache.get(key, String.class).filter(v -> !v.isBlank());
        if (version.isPresent()) {
            return version.get();
        }
        // Missing, or unreadable - the cache cannot say which. Write a fresh token and read the key
        // again: another replica may have won the race, and its token is as good as ours.
        cache.put(key, newVersion(), null);
        return cache.get(key, String.class)
                .filter(v -> !v.isBlank())
                .orElseThrow(() -> new CacheOperationException(
                        "Permission version for tenant " + tenantId + " could not be read or written"));
    }

    private static String newVersion() {
        return UUID.randomUUID().toString();
    }
}
