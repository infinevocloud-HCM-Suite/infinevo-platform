package com.infinevo.core.ai;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The signed-in user's AI helper chats (W-114.3): create, list, read messages, delete. Each endpoint needs
 * {@code core.ai.use}; the tenant is the bound one and the user the token subject, never a path or body value.
 *
 * <p>Create also needs the customer's switch on ({@link AiAccessGate#status()}, W-114.1) and answers
 * {@code 403 AI_DISABLED} when it is off. Reading and deleting existing chats does not: a user may still clear their
 * own history after the helper is switched off.
 */
@RestController
@RequestMapping("/api/v1/ai/sessions")
public class AiSessionController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    private final AiSessionService sessionService;
    private final AiAccessGate aiAccessGate;

    public AiSessionController(AiSessionService sessionService, AiAccessGate aiAccessGate) {
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
        this.aiAccessGate = Objects.requireNonNull(aiAccessGate, "aiAccessGate must not be null");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.ai.use")
    public AiSessionResponse create(@RequestBody(required = false) CreateAiSessionRequest request) {
        if (!aiAccessGate.status().enabled()) {
            throw new AiDisabledException();
        }
        return sessionService.create(request == null ? null : request.title());
    }

    /** A page of the caller's chats, newest activity first. {@code size} defaults to 20 and is held to 50. */
    @GetMapping
    @RequiresAction("core.ai.use")
    public Page<AiSessionResponse> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        return sessionService.listMine(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
    }

    @GetMapping("/{id}/messages")
    @RequiresAction("core.ai.use")
    public List<AiMessageResponse> messages(@PathVariable("id") UUID id) {
        return sessionService.messages(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAction("core.ai.use")
    public void delete(@PathVariable("id") UUID id) {
        sessionService.delete(id);
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
