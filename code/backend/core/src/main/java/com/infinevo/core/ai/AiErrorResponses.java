package com.infinevo.core.ai;

import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The AI helper error answers (W-114.1; {@code AI_RATE_LIMITED} W-114.4), in the shared {@link ApiErrorResponse}
 * envelope.
 *
 * <p>{@code ApiError} in {@code shared} has neither code, so the envelope is built through its record constructor
 * with the code as text, keeping the change inside {@code core}. Exception handlers are per controller in this
 * codebase: every controller that calls {@link AiAccessGate} declares the two handlers and calls these.
 */
public final class AiErrorResponses {

    public static final String AI_DISABLED = "AI_DISABLED";
    public static final String AI_CAP_REACHED = "AI_CAP_REACHED";
    public static final String AI_RATE_LIMITED = "AI_RATE_LIMITED";

    private AiErrorResponses() {}

    /** {@code 403 AI_DISABLED}. */
    public static ResponseEntity<ApiErrorResponse> disabled(AiDisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(AI_DISABLED, e.getMessage()));
    }

    /** {@code 429 AI_CAP_REACHED}. */
    public static ResponseEntity<ApiErrorResponse> capReached(AiCapReachedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body(AI_CAP_REACHED, e.getMessage()));
    }

    /** {@code 429 AI_RATE_LIMITED} with {@code Retry-After} in whole seconds (W-114.4, "Per-user rate limit"). */
    public static ResponseEntity<ApiErrorResponse> rateLimited(AiRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
                .body(body(AI_RATE_LIMITED, e.getMessage()));
    }

    private static ApiErrorResponse body(String code, String message) {
        return new ApiErrorResponse(code, message, Map.of(), traceId(), Instant.now());
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
