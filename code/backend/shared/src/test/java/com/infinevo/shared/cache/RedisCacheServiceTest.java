package com.infinevo.shared.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SuppressWarnings("unchecked")
class RedisCacheServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private RedisCacheService cacheService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        cacheService = new RedisCacheService(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("get returns cached value when present")
    void getReturnsCachedValue() {
        when(valueOperations.get("k1")).thenReturn("val1");

        Optional<String> result = cacheService.get("k1", String.class);
        assertTrue(result.isPresent());
        assertEquals("val1", result.get());
    }

    @Test
    @DisplayName("get returns empty optional when key does not exist")
    void getReturnsEmptyWhenKeyMissing() {
        when(valueOperations.get("k1")).thenReturn(null);

        Optional<String> result = cacheService.get("k1", String.class);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("get fails open and returns empty when Redis throws connection exception")
    void getFailsOpenOnConnectionError() {
        when(valueOperations.get("k1")).thenThrow(new RedisConnectionFailureException("Connection refused"));

        Optional<String> result = cacheService.get("k1", String.class);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("getOrCompute returns cached value on hit without invoking loader")
    void getOrComputeReturnsCachedOnHit() {
        when(valueOperations.get("k1")).thenReturn("cached-val");

        String result = cacheService.getOrCompute("k1", String.class, Duration.ofMinutes(5), () -> "fresh-val");
        assertEquals("cached-val", result);
        verify(valueOperations, times(0)).set(eq("k1"), any(), any());
    }

    @Test
    @DisplayName("getOrCompute invokes loader on cache miss and stores result")
    void getOrComputeInvokesLoaderOnMiss() {
        when(valueOperations.get("k1")).thenReturn(null);

        String result = cacheService.getOrCompute("k1", String.class, Duration.ofMinutes(5), () -> "fresh-val");
        assertEquals("fresh-val", result);
        verify(valueOperations).set("k1", "fresh-val", Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("getOrCompute fails open to loader when Redis throws connection error")
    void getOrComputeFailsOpenToLoader() {
        when(valueOperations.get("k1")).thenThrow(new RedisConnectionFailureException("Timeout"));

        String result = cacheService.getOrCompute("k1", String.class, Duration.ofMinutes(5), () -> "db-fallback");
        assertEquals("db-fallback", result);
    }

    @Test
    @DisplayName("evict deletes key from Redis")
    void evictDeletesKey() {
        cacheService.evict("k1");
        verify(redisTemplate).delete("k1");
    }

    @Test
    @DisplayName("evict fails closed and throws CacheOperationException when Redis fails")
    void evictFailsClosedOnRedisError() {
        doThrow(new RuntimeException("Redis unavailable")).when(redisTemplate).delete("k1");

        assertThrows(CacheOperationException.class, () -> cacheService.evict("k1"));
    }

    @Test
    @DisplayName("evictPattern queries keys and deletes all matching keys")
    void evictPatternDeletesAllMatches() {
        when(redisTemplate.keys("k:*")).thenReturn(Set.of("k:1", "k:2"));

        cacheService.evictPattern("k:*");
        verify(redisTemplate).delete(Set.of("k:1", "k:2"));
    }

    @Test
    @DisplayName("evictPattern fails closed and throws CacheOperationException on error")
    void evictPatternFailsClosedOnError() {
        when(redisTemplate.keys("k:*")).thenThrow(new RuntimeException("Scan failed"));

        assertThrows(CacheOperationException.class, () -> cacheService.evictPattern("k:*"));
    }

    @Test
    @DisplayName("isAvailable returns true when Redis ping answers PONG")
    void isAvailableReturnsTrueOnPong() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenReturn("PONG");

        assertTrue(cacheService.isAvailable());
    }

    @Test
    @DisplayName("isAvailable returns false when Redis ping throws error")
    void isAvailableReturnsFalseOnError() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenThrow(new RuntimeException("Network down"));

        assertFalse(cacheService.isAvailable());
    }

    @Test
    @DisplayName("Serializes and deserializes Set<String> via ObjectMapper cleanly")
    void jsonSerializationRoundTripForSet() throws Exception {
        Set<String> original = Set.of("VIEW_PROFILE", "APPLY_LEAVE", "VIEW_PAYSLIP");
        when(valueOperations.get("k-set")).thenReturn(objectMapper.writeValueAsString(original));

        Optional<Set> retrieved = cacheService.get("k-set", Set.class);
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().contains("VIEW_PROFILE"));
        assertTrue(retrieved.get().contains("VIEW_PAYSLIP"));
    }
}
