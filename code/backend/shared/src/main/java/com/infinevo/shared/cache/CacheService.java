package com.infinevo.shared.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Distributed cache abstraction interface for Infinevo Platform.
 *
 * <p>Backed by Redis in clustered and single-node deployments.
 */
public interface CacheService {

    /**
     * Retrieves a cached value by key.
     *
     * @param key Fully-qualified cache key
     * @param targetClass Expected type of the cached value
     * @param <T> Return type
     * @return Optional containing the cached value, or empty if missing/unavailable
     */
    <T> Optional<T> get(String key, Class<T> targetClass);

    /**
     * Retrieves a cached value, or computes and stores it if missing.
     *
     * <p>If cache retrieval fails due to Redis downtime, falls back to the loader directly.
     *
     * @param key Fully-qualified cache key
     * @param targetClass Expected type of the value
     * @param ttl Time-to-live duration for newly stored entry
     * @param loader Supplier function to compute the value on cache miss
     * @param <T> Return type
     * @return Cached or freshly computed value
     */
    <T> T getOrCompute(String key, Class<T> targetClass, Duration ttl, Supplier<T> loader);

    /**
     * Stores a value in the cache with the given time-to-live.
     *
     * @param key Fully-qualified cache key
     * @param value Value to cache
     * @param ttl Time-to-live duration
     */
    void put(String key, Object value, Duration ttl);

    /**
     * Evicts a single key from the cache immediately.
     *
     * @param key Fully-qualified cache key
     */
    void evict(String key);

    /**
     * Evicts all keys matching a glob-style pattern (e.g. {@code infinevo:{tenantId}:auth:perm:*}).
     *
     * @param pattern Key pattern to evict
     */
    void evictPattern(String pattern);

    /**
     * Checks if the underlying cache infrastructure is reachable.
     *
     * @return {@code true} if cache responds to ping, {@code false} otherwise
     */
    boolean isAvailable();
}
