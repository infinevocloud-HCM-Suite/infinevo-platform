package com.infinevo.shared.cache;

/**
 * Thrown when a critical cache operation (such as invalidation) fails.
 */
public class CacheOperationException extends RuntimeException {

    public CacheOperationException(String message) {
        super(message);
    }

    public CacheOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
