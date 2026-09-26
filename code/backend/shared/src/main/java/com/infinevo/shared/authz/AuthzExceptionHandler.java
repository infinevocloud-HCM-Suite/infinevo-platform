package com.infinevo.shared.authz;

import com.infinevo.shared.entitlement.EntitlementDeniedException;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a refused permission {@code 403 FORBIDDEN} in the shared {@link ApiErrorResponse} envelope
 * (W-11.2, spec section 13 decision 2; W-12.2).
 *
 * <p>Without it the exception would leave the dispatcher and reach Spring Security's
 * {@code ExceptionTranslationFilter}, whose bearer-token handler answers {@code 403} with an empty
 * body — a status with no code for the client to branch on. Every other refusal on the platform
 * already carries the envelope ({@code TenantContextFilter} writes {@code FORBIDDEN} the same way).
 *
 * <p>Handles every {@link AccessDeniedException}, not only {@link PermissionDeniedException}, so a
 * later {@code @PreAuthorize} refuses in the same shape. Controllers' own handlers still win over
 * this advice; none today catches {@code AccessDeniedException} or its parents.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthzExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        if (e instanceof EntitlementDeniedException denied) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiErrorResponse.of(denied.error(), denied.getMessage(), traceId()));
        }
        String message = e instanceof PermissionDeniedException denied
                ? ApiError.FORBIDDEN.defaultMessage() + ": requires action '" + denied.actionCode() + "'"
                : ApiError.FORBIDDEN.defaultMessage();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(ApiError.FORBIDDEN, message, traceId()));
    }

    /** The correlation id the logging filter put on this request, as the controllers' handlers do. */
    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
