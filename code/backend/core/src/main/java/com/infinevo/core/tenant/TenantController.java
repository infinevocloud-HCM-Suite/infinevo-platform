package com.infinevo.core.tenant;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for tenant provisioning and overview queries (W-12.1, W-65.1), and the bound tenant's own
 * profile (W-73.1).
 *
 * <p>Provisioning and the overviews are restricted to platform administrators holding
 * {@code core.tenant.provision}. {@code /current/profile} is the signed-in tenant's: {@code core.tenant.read}
 * to see it, {@code core.tenant.manage} - the catalogue's "change the tenant's own settings" code
 * ({@code reference/V020__action.sql:46}) - to change it.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final TenantQueryService tenantQueryService;
    private final TenantProfileService tenantProfileService;

    public TenantController(
            TenantService tenantService,
            TenantQueryService tenantQueryService,
            TenantProfileService tenantProfileService) {
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
        this.tenantQueryService = Objects.requireNonNull(tenantQueryService, "tenantQueryService must not be null");
        this.tenantProfileService =
                Objects.requireNonNull(tenantProfileService, "tenantProfileService must not be null");
    }

    /** The bound tenant's name, tagline and logo (W-73.1). */
    @GetMapping("/current/profile")
    @RequiresAction("core.tenant.read")
    public ResponseEntity<TenantProfileResponse> currentProfile() {
        return ResponseEntity.ok(tenantProfileService.current());
    }

    /** Sets the bound tenant's tagline and logo (W-73.1). {@code 400} names the field that was refused. */
    @PutMapping("/current/profile")
    @RequiresAction("core.tenant.manage")
    public ResponseEntity<TenantProfileResponse> updateCurrentProfile(@RequestBody TenantProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        return ResponseEntity.ok(tenantProfileService.update(request));
    }

    @PostMapping
    @RequiresAction("core.tenant.provision")
    public ResponseEntity<TenantResponse> createTenant(@RequestBody TenantRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        // D-42: the provisioning user is recorded as the inviter of the tenant administrator.
        TenantResponse response = tenantService.provisionTenant(request, currentActorUserId());
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + response.tenantId()))
                .body(response);
    }

    @GetMapping
    @RequiresAction("core.tenant.provision")
    public ResponseEntity<List<TenantOverview>> listTenants() {
        return ResponseEntity.ok(tenantQueryService.list());
    }

    /** The platform dashboard (W-73.2): counts by status, recent tenants, administrators still to accept. */
    @GetMapping("/summary")
    @RequiresAction("core.tenant.provision")
    public ResponseEntity<TenantSummaryResponse> summary() {
        return ResponseEntity.ok(tenantQueryService.summary());
    }

    /**
     * Sends a tenant's waiting administrator invitation again (W-73.2). {@code 404} for an unknown tenant,
     * {@code 409} when no administrator invitation is pending or expired.
     */
    @PostMapping("/{id}/admin-invitation/resend")
    @RequiresAction("core.tenant.provision")
    public ResponseEntity<Void> resendAdminInvitation(@PathVariable("id") UUID id) {
        tenantService.resendAdminInvitation(id, currentActorUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @RequiresAction("core.tenant.provision")
    public ResponseEntity<TenantOverview> getTenant(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(tenantQueryService.getOverview(id));
    }

    /** The Keycloak subject of the caller, as {@code UserInvitationController} resolves it; null if none. */
    private static UUID currentActorUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                try {
                    return UUID.fromString(subject);
                } catch (IllegalArgumentException ignored) {
                    // not a UUID subject: no inviter can be recorded
                }
            }
        }
        return null;
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantNotFound(TenantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.TENANT_NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(AdminInvitationNotWaitingException.class)
    public ResponseEntity<ApiErrorResponse> handleNotWaiting(AdminInvitationNotWaitingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    /**
     * The resend refused by the invitation service (W-73.2): a pending invitation to the same address already
     * exists, or the invitation expired between the read and the resend ({@code InvitationExpiredException} is an
     * {@code IllegalStateException}).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
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
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
