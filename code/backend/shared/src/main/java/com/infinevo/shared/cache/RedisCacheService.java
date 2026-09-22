package com.infinevo.shared.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Production Redis implementation of {@link CacheService}.
 *
 * <p>Implements:
 * <ul>
 *   <li><strong>Read fail-open:</strong> Redis connection timeouts or outages log warnings
 *       and return {@link Optional#empty()} or trigger the fallback loader, ensuring
 *       uninterrupted customer traffic.</li>
 *   <li><strong>Invalidation fail-closed:</strong> Invalidation errors throw
 *       {@link CacheOperationException} so stale authorization state is never tolerated.</li>
 * </ul>
 */
public class RedisCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(targetClass, "targetClass must not be null");

        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            if (String.class.equals(targetClass)) {
                return Optional.of(targetClass.cast(raw));
            }
            return Optional.of(objectMapper.readValue(raw, targetClass));
        } catch (Exception e) {
            log.warn("Redis read failed for key '{}'. Failing open to source: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public <T> T getOrCompute(String key, Class<T> targetClass, Duration ttl, Supplier<T> loader) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(targetClass, "targetClass must not be null");
        Objects.requireNonNull(loader, "loader must not be null");

        Optional<T> cached = get(key, targetClass);
        if (cached.isPresent()) {
            return cached.get();
        }

        T fresh = loader.get();
        if (fresh != null) {
            put(key, fresh, ttl);
        }
        return fresh;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                redisTemplate.opsForValue().set(key, json, ttl);
            } else {
                redisTemplate.opsForValue().set(key, json);
            }
        } catch (Exception e) {
            log.warn("Redis write failed for key '{}': {}", key, e.getMessage());
        }
    }

    @Override
    public void evict(String key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            redisTemplate.delete(key);
            log.debug("Evicted cache key '{}'", key);
        } catch (Exception e) {
            log.error("Failed to evict cache key '{}': {}", key, e.getMessage(), e);
            throw new CacheOperationException("Failed to evict cache key: " + key, e);
        }
    }

    @Override
    public void evictPattern(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Evicted {} keys matching pattern '{}'", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("Failed to evict cache keys matching pattern '{}': {}", pattern, e.getMessage(), e);
            throw new CacheOperationException("Failed to evict pattern: " + pattern, e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
            if (factory == null) {
                return false;
            }
            try (RedisConnection conn = factory.getConnection()) {
                String reply = conn.ping();
                return "PONG".equalsIgnoreCase(reply);
            }
        } catch (Exception e) {
            log.warn("Redis connectivity check failed: {}", e.getMessage());
            return false;
        }
    }
}
