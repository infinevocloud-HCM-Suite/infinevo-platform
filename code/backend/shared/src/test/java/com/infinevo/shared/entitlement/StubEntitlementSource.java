package com.infinevo.shared.entitlement;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link EntitlementSource} whose answer the test changes — test double for W-12.2.
 * Counts its calls, so a test can tell a cache hit from a reload.
 */
public final class StubEntitlementSource implements EntitlementSource {

    private final ConcurrentHashMap<UUID, Set<PlatformModule>> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<PlatformModule>> revoked = new ConcurrentHashMap<>();
    private final Set<UUID> suspendedTenants = ConcurrentHashMap.newKeySet();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile RuntimeException failure;

    public void grant(UUID tenantId, PlatformModule... modules) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        java.util.Set<PlatformModule> toGrant = java.util.Set.of(modules);
        active.compute(tenantId, (id, current) -> {
            java.util.Set<PlatformModule> s =
                    current == null ? new java.util.HashSet<>() : new java.util.HashSet<>(current);
            s.addAll(toGrant);
            return java.util.Collections.unmodifiableSet(s);
        });
        revoked.computeIfPresent(tenantId, (id, current) -> {
            java.util.Set<PlatformModule> s = new java.util.HashSet<>(current);
            s.removeAll(toGrant);
            return java.util.Collections.unmodifiableSet(s);
        });
    }

    public void revoke(UUID tenantId, PlatformModule... modules) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        java.util.Set<PlatformModule> toRevoke = java.util.Set.of(modules);
        active.computeIfPresent(tenantId, (id, current) -> {
            java.util.Set<PlatformModule> s = new java.util.HashSet<>(current);
            s.removeAll(toRevoke);
            return java.util.Collections.unmodifiableSet(s);
        });
        revoked.compute(tenantId, (id, current) -> {
            java.util.Set<PlatformModule> s =
                    current == null ? new java.util.HashSet<>() : new java.util.HashSet<>(current);
            s.addAll(toRevoke);
            return java.util.Collections.unmodifiableSet(s);
        });
    }

    public void set(UUID tenantId, Set<PlatformModule> activeModules, Set<PlatformModule> revokedModules) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        active.put(tenantId, activeModules != null ? Set.copyOf(activeModules) : Set.of());
        revoked.put(tenantId, revokedModules != null ? Set.copyOf(revokedModules) : Set.of());
    }

    public void setSuspended(UUID tenantId, boolean suspended) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (suspended) {
            suspendedTenants.add(tenantId);
        } else {
            suspendedTenants.remove(tenantId);
        }
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public Set<PlatformModule> modulesOf(UUID tenantId) {
        return snapshotOf(tenantId).activeModules();
    }

    @Override
    public boolean isSuspended(UUID tenantId) {
        return snapshotOf(tenantId).suspended();
    }

    @Override
    public Set<PlatformModule> revokedModulesOf(UUID tenantId) {
        return snapshotOf(tenantId).revokedModules();
    }

    @Override
    public EntitlementSnapshot snapshotOf(UUID tenantId) {
        calls.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        if (tenantId == null) {
            return EntitlementSnapshot.empty();
        }
        boolean isSuspended = suspendedTenants.contains(tenantId);
        if (isSuspended) {
            return new EntitlementSnapshot(Set.of(), Set.of(), true);
        }
        return new EntitlementSnapshot(
                active.getOrDefault(tenantId, Set.of()), revoked.getOrDefault(tenantId, Set.of()), false);
    }
}
