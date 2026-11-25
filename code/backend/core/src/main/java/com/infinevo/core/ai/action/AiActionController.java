package com.infinevo.core.ai.action;

import com.infinevo.core.ai.AiDisabledException;
import com.infinevo.core.ai.AiErrorResponses;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The user's side of a proposed action (W-114.8): confirm, cancel and read. These are endpoints the signed-in user
 * calls from the browser; no tool reaches them, so no model output can (rule C11). Confirm and cancel take no request
 * body - a body, if sent, is never read: what runs is the stored payload (rule C8).
 *
 * <p>Its own handlers, as handlers are per controller here: {@code 404} for an intent that is not the caller's,
 * {@code 410 AI_ACTION_EXPIRED}, {@code 409 AI_ACTION_CANCELLED}, {@code 409 AI_ACTION_FAILED},
 * {@code 409 AI_ACTION_CONFIRMED} and {@code 403 AI_DISABLED}. A permission, module or employee record gone is an
 * {@code AccessDeniedException}, answered {@code 403 FORBIDDEN} by the shared handler.
 */
@RestController
@RequestMapping("/api/v1/ai/actions")
public class AiActionController {

    public static final String AI_ACTION_EXPIRED = "AI_ACTION_EXPIRED";
    public static final String AI_ACTION_CANCELLED = "AI_ACTION_CANCELLED";
    public static final String AI_ACTION_FAILED = "AI_ACTION_FAILED";
    public static final String AI_ACTION_CONFIRMED = "AI_ACTION_CONFIRMED";

    private final AiActionService actionService;

    public AiActionController(AiActionService actionService) {
        this.actionService = Objects.requireNonNull(actionService, "actionService must not be null");
    }

    @PostMapping("/{intentId}/confirm")
    @RequiresAction("core.ai.use")
    public AiActionOutcome confirm(@PathVariable("intentId") UUID intentId) {
        return actionService.confirm(intentId);
    }

    @PostMapping("/{intentId}/cancel")
    @RequiresAction("core.ai.use")
    public AiActionOutcome cancel(@PathVariable("intentId") UUID intentId) {
        return actionService.cancel(intentId);
    }

    @GetMapping("/{intentId}")
    @RequiresAction("core.ai.use")
    public AiActionView get(@PathVariable("intentId") UUID intentId) {
        return actionService.get(intentId);
    }

    @ExceptionHandler(AiActionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(AiActionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, "The proposed action was not found.", traceId()));
    }

    @ExceptionHandler(AiActionExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleExpired(AiActionExpiredException e) {
        return error(HttpStatus.GONE, AI_ACTION_EXPIRED, e.getMessage());
    }

    @ExceptionHandler(AiActionCancelledException.class)
    public ResponseEntity<ApiErrorResponse> handleCancelled(AiActionCancelledException e) {
        return error(HttpStatus.CONFLICT, AI_ACTION_CANCELLED, e.getMessage());
    }

    @ExceptionHandler(AiActionFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleFailed(AiActionFailedException e) {
        return error(HttpStatus.CONFLICT, AI_ACTION_FAILED, e.getMessage());
    }

    @ExceptionHandler(AiActionConfirmedException.class)
    public ResponseEntity<ApiErrorResponse> handleConfirmed(AiActionConfirmedException e) {
        return error(HttpStatus.CONFLICT, AI_ACTION_CONFIRMED, e.getMessage());
    }

    @ExceptionHandler(AiDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(AiDisabledException e) {
        return AiErrorResponses.disabled(e);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, Map.of(), traceId(), Instant.now()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
