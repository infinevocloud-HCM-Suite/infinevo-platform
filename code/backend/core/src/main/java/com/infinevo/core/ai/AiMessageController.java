package com.infinevo.core.ai;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Asks the AI helper a question in one of the caller's chats (W-114.4): {@code POST /api/v1/ai/sessions/{id}/messages}.
 * Needs {@code core.ai.use}; the chat must be the caller's. One full answer per request, no streaming.
 *
 * <p>Its own handlers, as handlers are per controller here: {@code 403 AI_DISABLED}, {@code 429 AI_CAP_REACHED},
 * {@code 404} for a chat that is not the caller's, {@code 400} for an empty or too long question, and {@code 502}
 * {@code INTERNAL} when the model fails or runs out of time.
 */
@RestController
@RequestMapping("/api/v1/ai/sessions")
public class AiMessageController {

    static final String MODEL_FAILED = "The helper could not answer just now. Please try again.";

    private static final Logger log = LoggerFactory.getLogger(AiMessageController.class);

    private final AiChatService chatService;

    public AiMessageController(AiChatService chatService) {
        this.chatService = Objects.requireNonNull(chatService, "chatService must not be null");
    }

    @PostMapping("/{id}/messages")
    @RequiresAction("core.ai.use")
    public AiChatResponse send(@PathVariable("id") UUID id, @RequestBody(required = false) AiMessageRequest request) {
        // request.context() is accepted and ignored until W-114.13.
        return chatService.send(id, request == null ? null : request.content());
    }

    @ExceptionHandler(AiSessionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(AiSessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return badRequest(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("Invalid value for " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return badRequest("The request body is not valid JSON");
    }

    @ExceptionHandler(AiDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(AiDisabledException e) {
        return AiErrorResponses.disabled(e);
    }

    @ExceptionHandler(AiCapReachedException.class)
    public ResponseEntity<ApiErrorResponse> handleCapReached(AiCapReachedException e) {
        return AiErrorResponses.capReached(e);
    }

    @ExceptionHandler(AiModelException.class)
    public ResponseEntity<ApiErrorResponse> handleModelFailed(AiModelException e) {
        // The exception names a status or a cause, never the question or an answer.
        log.warn("AI helper model call failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.of(ApiError.INTERNAL, MODEL_FAILED, traceId()));
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, message, traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
