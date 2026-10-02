package com.infinevo.shared.impersonation;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.PlatformTenant;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for opening and closing platform staff impersonation sessions (W-65.2).
 *
 * <p>Both endpoints require the caller to hold {@code core.tenant.impersonate} and to be
 * currently bound to the Infinevo platform tenant.
 */
@RestController
@RequestMapping("/api/v1")
public class ImpersonationController {

    private final ImpersonationService impersonationService;
    private final PlatformTenant platformTenant;

    public ImpersonationController(ImpersonationService impersonationService, PlatformTenant platformTenant) {
        this.impersonationService =
                Objects.requireNonNull(impersonationService, "impersonationService must not be null");
        this.platformTenant = Objects.requireNonNull(platformTenant, "platformTenant must not be null");
    }

    /** Request payload to open an impersonation session. */
    public record OpenImpersonationRequest(UUID userAccountId, String email, String reason) {}

    /** Response payload returning the newly opened impersonation session. */
    public record OpenImpersonationResponse(
            UUID sessionId,
            UUID tenantId,
            UUID userAccountId,
            String userEmail,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt) {

        public static OpenImpersonationResponse from(ImpersonationService.OpenedImpersonation opened) {
            return new OpenImpersonationResponse(
                    opened.sessionId(),
                    opened.tenantId(),
                    opened.userAccountId(),
                    opened.userEmail(),
                    opened.expiresAt());
        }
    }

    @PostMapping("/tenants/{id}/impersonations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.tenant.impersonate")
    public OpenImpersonationResponse openImpersonation(
            @PathVariable("id") UUID tenantId, @RequestBody OpenImpersonationRequest request, Authentication auth) {
        platformTenant.requirePlatformTenant();
        UUID staffUserId = resolveCallerUserId(auth);
        ImpersonationService.OpenedImpersonation opened = impersonationService.openImpersonation(
                tenantId,
                staffUserId,
                request != null ? request.userAccountId() : null,
                request != null ? request.email() : null,
                request != null ? request.reason() : null);
        return OpenImpersonationResponse.from(opened);
    }

    @DeleteMapping("/impersonations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAction("core.tenant.impersonate")
    public void closeImpersonation(@PathVariable("id") UUID sessionId, Authentication auth) {
        platformTenant.requirePlatformTenant();
        UUID staffUserId = resolveCallerUserId(auth);
        impersonationService.closeImpersonation(sessionId, staffUserId);
    }

    @ExceptionHandler(ImpersonationService.UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(ImpersonationService.UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.USER_NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ImpersonationService.TenantNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantNotFound(ImpersonationService.TenantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.TENANT_NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ImpersonationService.SessionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSessionNotFound(ImpersonationService.SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON format.",
                        traceId()));
    }

    private static UUID resolveCallerUserId(Authentication auth) {
        if (auth == null) {
            auth = SecurityContextHolder.getContext().getAuthentication();
        }
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication required");
        }
        String subject = auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : auth.getName();
        if (subject == null || subject.isBlank()) {
            throw new AccessDeniedException("Authentication subject missing");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Token subject is not a UUID: " + subject);
        }
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
