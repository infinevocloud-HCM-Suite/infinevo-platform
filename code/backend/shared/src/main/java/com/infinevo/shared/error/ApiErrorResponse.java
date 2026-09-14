package com.infinevo.shared.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The response body every failing endpoint returns. No endpoint invents its own shape.
 *
 * <p>The two frozen applications each return several different error shapes, so their
 * frontends parse failure differently per screen. One envelope is what lets the API client
 * handle errors in a single place.
 *
 * @param code        stable and machine-readable - clients branch on this
 * @param message     human-readable, safe to display. Never a stack trace or SQL
 * @param fieldErrors per-field detail for validation failures; empty otherwise
 * @param traceId     correlates this response with the server logs
 * @param timestamp   when the failure occurred
 */
public record ApiErrorResponse(
        String code, String message, Map<String, String> fieldErrors, String traceId, Instant timestamp) {

    public static ApiErrorResponse of(ApiError error, String traceId) {
        return new ApiErrorResponse(error.code(), error.defaultMessage(), Map.of(), traceId, Instant.now());
    }

    public static ApiErrorResponse of(ApiError error, String message, String traceId) {
        return new ApiErrorResponse(error.code(), message, Map.of(), traceId, Instant.now());
    }

    public static ApiErrorResponse validation(Map<String, String> fieldErrors, String traceId) {
        return new ApiErrorResponse(
                ApiError.VALIDATION_FAILED.code(),
                ApiError.VALIDATION_FAILED.defaultMessage(),
                Map.copyOf(fieldErrors),
                traceId,
                Instant.now());
    }

    public List<String> fields() {
        return List.copyOf(fieldErrors.keySet());
    }
}
