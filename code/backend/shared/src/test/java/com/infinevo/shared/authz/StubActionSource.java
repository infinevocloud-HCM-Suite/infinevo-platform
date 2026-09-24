package com.infinevo.shared.authz;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link ActionSource} whose answer the test changes — the stand-in for {@code W-11.1}'s tables.
 * Counts its calls, so a test can tell a cache hit from a reload.
 */
final class StubActionSource implements ActionSource {

    private final ConcurrentHashMap<String, Set<String>> grants = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile RuntimeException failure;

    void grant(UUID tenantId, UUID userAccountId, String... actions) {
        grants.put(tenantId + "/" + userAccountId, Set.of(actions));
    }

    void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    int calls() {
        return calls.get();
    }

    @Override
    public Set<String> actionsOf(UUID tenantId, UUID userAccountId) {
        calls.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        return grants.getOrDefault(tenantId + "/" + userAccountId, Set.of());
    }
}
