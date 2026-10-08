package com.infinevo.core.invitation;

import com.infinevo.shared.authz.PermissionService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for employee invitations ({@code /api/v1/employee-invitations}) (W-24.2, spec section 4).
 *
 * <p>Guarded by {@code core.employee.create}. An invitation that carries extra roles (W-73.3) also needs
 * {@code core.role.assign}, the action that guards {@code PUT /api/v1/users/{id}/roles}.
 */
@RestController
@RequestMapping("/api/v1/employee-invitations")
public class EmployeeInvitationController {

    /** Granting roles through an invitation needs the same action as granting them directly. */
    static final String ROLE_ASSIGN_ACTION = "core.role.assign";

    private final InvitationService invitationService;
    private final PermissionService permissionService;

    public EmployeeInvitationController(InvitationService invitationService, PermissionService permissionService) {
        this.invitationService = Objects.requireNonNull(invitationService, "invitationService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    /**
     * {@code 201}; {@code 403} without {@code core.employee.create}, or with non-empty {@code roleIds} and
     * without {@code core.role.assign} — otherwise HR could hand a new hire {@code tenant-admin} and bypass
     * the guard on {@code PUT /users/{id}/roles}. Checked here, before any transaction opens.
     *
     * <p>W-73.7 bulk invite must apply the same rule to every row that carries roles.
     */
    @PostMapping
    @RequiresAction("core.employee.create")
    public ResponseEntity<EmployeeInvitationResponse> create(@RequestBody EmployeeInvitationRequest request) {
        if (request != null && request.roleIds() != null && !request.roleIds().isEmpty()) {
            permissionService.require(ROLE_ASSIGN_ACTION);
        }
        EmployeeInvitationResponse response = invitationService.createEmployeeInvitation(request, currentActorUserId());
        return ResponseEntity.created(URI.create("/api/v1/employee-invitations/" + response.id()))
                .body(response);
    }

    @GetMapping
    @RequiresAction("core.employee.create")
    public List<EmployeeInvitationResponse> list(
            @RequestParam(name = "status", required = false) InvitationStatus status,
            @RequestParam(name = "employeeId", required = false) UUID employeeId) {
        return invitationService.listEmployeeInvitations(status, employeeId);
    }

    @PostMapping("/{id}/resend")
    @RequiresAction("core.employee.create")
    public ResponseEntity<EmployeeInvitationResponse> resend(@PathVariable("id") UUID id) {
        EmployeeInvitationResponse response = invitationService.resendEmployeeInvitation(id, currentActorUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/revoke")
    @RequiresAction("core.employee.create")
    public ResponseEntity<Void> revoke(@PathVariable("id") UUID id) {
        invitationService.revokeEmployeeInvitation(id, currentActorUserId());
        return ResponseEntity.ok().build();
    }

    private static UUID currentActorUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                try {
                    return UUID.fromString(subject);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
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
