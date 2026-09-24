package com.infinevo.shared.authz;

import static com.infinevo.shared.authz.AuthzTestSupport.authenticate;
import static com.infinevo.shared.authz.AuthzTestSupport.identityResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.cache.CacheOperationException;
import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * W-11.2 spec section 7 — the version key, the miss path, and the cache failing closed.
 */
class PermissionCacheTest {

    private static final String READ = "core.employee.read";
    private static final String UPDATE = "core.employee.update";

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private FakeCacheService cache;
    private StubActionSource source;
    private PermissionCache permissionCache;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        cache = new FakeCacheService();
        source = new StubActionSource();
        source.grant(tenant, user, READ);
        permissionCache = new PermissionCache(cache);
        service = new PermissionService(() -> permissionCache, () -> source, identityResolver());
        TenantContext.set(tenant);
        authenticate(user);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A hit is served from the cache - the source is asked once")
    void hitIsServedFromCache() {
        assertThat(service.holds(READ)).isTrue();
        assertThat(service.holds(READ)).isTrue();
        assertThat(service.holds(UPDATE)).isFalse();

        assertThat(source.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("A version bump invalidates: the old set is served until the bump, the new one after")
    void versionBumpInvalidates() {
        assertThat(service.holds(UPDATE)).isFalse();

        source.grant(tenant, user, READ, UPDATE);
        assertThat(service.holds(UPDATE))
                .as("no bump yet - the cached set still answers, which proves it is a cache")
                .isFalse();

        permissionCache.bumpVersion(tenant);
        assertThat(service.holds(UPDATE)).isTrue();
        assertThat(source.calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("A miss repopulates: the entry is written, and written again after it is lost")
    void missRepopulates() {
        String key = PermissionCache.permissionKey(tenant, user, versionOf(tenant));
        assertThat(cache.contains(key)).isFalse();

        assertThat(service.holds(READ)).isTrue();
        assertThat(cache.contains(key)).isTrue();
        assertThat((String[]) cache.raw(key)).containsExactly(READ);

        cache.remove(key);
        assertThat(service.holds(READ)).isTrue();
        assertThat(cache.contains(key)).isTrue();
        assertThat(source.calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("An empty set is cached too - a user holding nothing does not reach the database every time")
    void emptySetIsCached() {
        UUID nobody = UUID.randomUUID();
        authenticate(nobody);

        assertThat(service.holds(READ)).isFalse();
        assertThat(service.holds(READ)).isFalse();
        assertThat(source.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("A missing version is created once and then stable")
    void missingVersionIsCreatedAndStable() {
        assertThat(cache.contains(PermissionCache.versionKey(tenant))).isFalse();
        service.holds(READ);
        String first = versionOf(tenant);
        service.holds(READ);

        assertThat(first).isNotBlank();
        assertThat(versionOf(tenant)).isEqualTo(first);
    }

    @Test
    @DisplayName(
            "Redis unreachable, as RedisCacheService reports it (reads empty, writes dropped): refused, not allowed")
    void unreachableCacheFailsClosed() {
        cache.mode(FakeCacheService.Mode.UNREACHABLE);

        assertThat(service.holds(READ))
                .as("the source would say yes; the check must still say no")
                .isFalse();
        assertThatThrownBy(() -> permissionCache.actionsOf(tenant, user, Set::of))
                .isInstanceOf(CacheOperationException.class);
    }

    @Test
    @DisplayName("A cache that throws: refused, not allowed")
    void throwingCacheFailsClosed() {
        cache.mode(FakeCacheService.Mode.THROWING);

        assertThat(service.holds(READ)).isFalse();
    }

    @Test
    @DisplayName("A flaky read of the version never serves a set from before the last change")
    void flakyVersionReadNeverServesStale() {
        assertThat(service.holds(UPDATE)).isFalse();
        source.grant(tenant, user, READ, UPDATE);

        // The version read fails once. Read as "version 0" - or as any version that has keyed a
        // set before - this would find the old set. A fresh token keys nothing, so it reloads.
        cache.failNextGets(1);
        assertThat(service.holds(UPDATE)).isTrue();
    }

    @Test
    @DisplayName("Tenants do not share entries, and bumping one leaves the other's version alone")
    void tenantsAreIsolated() {
        UUID other = UUID.randomUUID();
        source.grant(other, user, UPDATE);

        assertThat(service.holds(READ)).isTrue();
        TenantContext.set(other);
        assertThat(service.holds(READ)).isFalse();
        assertThat(service.holds(UPDATE)).isTrue();

        String otherVersion = versionOf(other);
        permissionCache.bumpVersion(tenant);
        assertThat(versionOf(other)).isEqualTo(otherVersion);
    }

    @Test
    @DisplayName("A bump that cannot reach the cache throws - the role change must not look invalidated")
    void bumpFailsLoudly() {
        cache.mode(FakeCacheService.Mode.UNREACHABLE);

        assertThatThrownBy(() -> permissionCache.bumpVersion(tenant)).isInstanceOf(CacheOperationException.class);
    }

    @Test
    @DisplayName("With no cache configured: a bump has nothing to invalidate, a lookup refuses")
    void noCacheConfigured() {
        PermissionCache none = new PermissionCache(() -> (CacheService) null);

        assertThatCode(() -> none.bumpVersion(tenant)).doesNotThrowAnyException();
        assertThatThrownBy(() -> none.actionsOf(tenant, user, Set::of)).isInstanceOf(CacheOperationException.class);
    }

    private String versionOf(UUID tenantId) {
        Object version = cache.raw(PermissionCache.versionKey(tenantId));
        if (version == null) {
            // Create it the way a reader would, so a key can be computed before the first check.
            permissionCache.actionsOf(tenantId, UUID.randomUUID(), Set::of);
            version = cache.raw(PermissionCache.versionKey(tenantId));
        }
        return (String) version;
    }
}
