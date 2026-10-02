package com.infinevo.core.tenant;

import com.infinevo.shared.cache.CacheService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A {@link CacheService} held in memory, for the integration tests whose subject is not the cache.
 *
 * <p>The impersonation tests need the real permission check, which keeps a tenant's permission version and each
 * user's action set in the cache. Redis adds nothing to what they prove, and with it the tests could not run
 * wherever Docker is unavailable. Time-to-live is ignored: a test is shorter than any TTL the cache uses.
 * {@code NavigationIT} and {@code PermissionGuardIT} are the tests that exercise the real Redis store.
 */
class InMemoryCacheService implements CacheService {

    private final Map<String, Object> store = new ConcurrentHashMap<>();

    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        return Optional.ofNullable(store.get(key)).map(targetClass::cast);
    }

    @Override
    public <T> T getOrCompute(String key, Class<T> targetClass, Duration ttl, Supplier<T> loader) {
        Object cached = store.get(key);
        if (cached != null) {
            return targetClass.cast(cached);
        }
        T loaded = loader.get();
        if (loaded != null) {
            store.put(key, loaded);
        }
        return loaded;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        if (value != null) {
            store.put(key, value);
        }
    }

    @Override
    public void evict(String key) {
        store.remove(key);
    }

    @Override
    public void evictPattern(String pattern) {
        Pattern glob = Pattern.compile(Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
        store.keySet().removeIf(key -> glob.matcher(key).matches());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
