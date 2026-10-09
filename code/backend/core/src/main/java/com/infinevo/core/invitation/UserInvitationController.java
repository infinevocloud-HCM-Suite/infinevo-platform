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
 * Controller for user invitations ({@code /api/v1/user-invitations}) (W-24.2, spec section 4).
 *
 * <p>Guarded by {@code core.user.manage}.
 */
@RestController
@RequestMapping("/api/v1/user-invitations")
public class UserInvitationController {

    private final InvitationService invitationService;
    private final PermissionService permissionService;

    public UserInvitationController(InvitationService invitationService, PermissionService permissionService) {
        this.invitationService = Objects.requireNonNull(invitationService, "invitationService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    /**
     * {@code 201}; {@code 403} without {@code core.user.manage}, or with non-empty {@code roleIds} and without
     * {@code core.role.assign} — the rule {@code EmployeeInvitationController.create} applies (W-73.3), so the
     * Users &amp; access invite cannot grant what {@code PUT /users/{id}/roles} would refuse (W-73.4).
     */
    @PostMapping
    @RequiresAction("core.user.manage")
    public ResponseEntity<UserInvitationResponse> create(@RequestBody UserInvitationRequest request) {
        if (request != null && request.roleIds() != null && !request.roleIds().isEmpty()) {
            permissionService.require(EmployeeInvitationController.ROLE_ASSIGN_ACTION);
        }
        UserInvitationResponse response = invitationService.createUserInvitation(request, currentActorUserId());
        return ResponseEntity.created(URI.create("/api/v1/user-invitations/" + response.id()))
                .body(response);
    }

    @GetMapping
    @RequiresAction("core.user.manage")
    public List<UserInvitationResponse> list(@RequestParam(name = "status", required = false) InvitationStatus status) {
        return invitationService.listUserInvitations(status);
    }

    @PostMapping("/{id}/resend")
    @RequiresAction("core.user.manage")
    public ResponseEntity<UserInvitationResponse> resend(@PathVariable("id") UUID id) {
        UserInvitationResponse response = invitationService.resendUserInvitation(id, currentActorUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/revoke")
    @RequiresAction("core.user.manage")
    public ResponseEntity<Void> revoke(@PathVariable("id") UUID id) {
        invitationService.revokeUserInvitation(id, currentActorUserId());
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
