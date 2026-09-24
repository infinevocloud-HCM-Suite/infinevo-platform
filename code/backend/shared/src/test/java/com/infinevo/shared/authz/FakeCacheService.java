package com.infinevo.shared.authz;

import com.infinevo.shared.cache.CacheOperationException;
import com.infinevo.shared.cache.CacheService;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * An in-process {@link CacheService} for the unit tests, with the failure behaviour of the real one.
 *
 * <p>{@link Mode#UNREACHABLE} copies {@code RedisCacheService} with Redis down: reads answer empty,
 * writes are dropped with no error, evictions throw {@link CacheOperationException}. That is the case
 * the permission check must survive — a cache that hides its own outage — so the fake has to hide it
 * the same way. {@link Mode#THROWING} is a cache that does not hide it.
 *
 * <p>A map is fine here: this is test code standing in for Redis. The rule against in-process maps
 * is for {@code src/main/.../authz/}.
 */
final class FakeCacheService implements CacheService {

    enum Mode {
        NORMAL,
        UNREACHABLE,
        THROWING
    }

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();
    private final AtomicInteger failNextGets = new AtomicInteger();
    private volatile Mode mode = Mode.NORMAL;

    void mode(Mode mode) {
        this.mode = mode;
    }

    /** The next {@code n} reads answer empty, as a flaky connection would under {@code RedisCacheService}. */
    void failNextGets(int n) {
        failNextGets.set(n);
    }

    boolean contains(String key) {
        return store.containsKey(key);
    }

    Object raw(String key) {
        return store.get(key);
    }

    void remove(String key) {
        store.remove(key);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        if (mode == Mode.THROWING) {
            throw new IllegalStateException("cache exploded");
        }
        if (mode == Mode.UNREACHABLE || failNextGets.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            return Optional.empty();
        }
        Object value = store.get(key);
        return targetClass.isInstance(value) ? Optional.of(targetClass.cast(value)) : Optional.empty();
    }

    @Override
    public <T> T getOrCompute(String key, Class<T> targetClass, Duration ttl, Supplier<T> loader) {
        return get(key, targetClass).orElseGet(() -> {
            T fresh = loader.get();
            put(key, fresh, ttl);
            return fresh;
        });
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        if (mode == Mode.THROWING) {
            throw new IllegalStateException("cache exploded");
        }
        if (mode == Mode.UNREACHABLE) {
            return;
        }
        store.put(key, value);
    }

    @Override
    public void evict(String key) {
        if (mode != Mode.NORMAL) {
            throw new CacheOperationException("Failed to evict cache key: " + key);
        }
        store.remove(key);
    }

    @Override
    public void evictPattern(String pattern) {
        throw new UnsupportedOperationException("the permission cache never evicts by pattern");
    }

    @Override
    public boolean isAvailable() {
        return mode == Mode.NORMAL;
    }
}
