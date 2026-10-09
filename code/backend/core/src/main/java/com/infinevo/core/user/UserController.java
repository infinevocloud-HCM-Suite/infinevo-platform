package com.infinevo.core.user;

import com.infinevo.core.authz.RoleService;
import com.infinevo.core.invitation.KeycloakProvisioningException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Users &amp; access (W-73.4 §4): {@code GET /api/v1/users}, {@code POST /api/v1/users/{id}/disable} and
 * {@code /enable}, all under {@code core.user.manage}. Changing a user's roles stays on
 * {@code UserRoleController} ({@code PUT /api/v1/users/{id}/roles}, {@code core.role.assign}).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    /** Disabling or enabling a tenant-admin changes who administers the tenant, as a role change does. */
    static final String ROLE_ASSIGN_ACTION = "core.role.assign";

    private final UserDirectoryService userDirectoryService;
    private final RoleService roleService;
    private final PermissionService permissionService;

    public UserController(
            UserDirectoryService userDirectoryService, RoleService roleService, PermissionService permissionService) {
        this.userDirectoryService =
                Objects.requireNonNull(userDirectoryService, "userDirectoryService must not be null");
        this.roleService = Objects.requireNonNull(roleService, "roleService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    /** {@code 200}: the bound tenant's accounts, with roles and linked employee. */
    @GetMapping
    @RequiresAction("core.user.manage")
    public List<UserView> list(@RequestParam(name = "q", required = false) String q) {
        return userDirectoryService.list(q);
    }

    /**
     * {@code 204}; {@code 403} when the target holds {@code tenant-admin} and the caller lacks
     * {@code core.role.assign}; {@code 404} not in this tenant; {@code 409} own account or last active
     * tenant-admin.
     */
    @PostMapping("/{id}/disable")
    @RequiresAction("core.user.manage")
    public ResponseEntity<Void> disable(@PathVariable("id") UUID id) {
        requireRoleAssignForTenantAdmin(id);
        userDirectoryService.disable(id);
        return ResponseEntity.noContent().build();
    }

    /** {@code 204}; {@code 403} as for disable; {@code 404} not in this tenant. */
    @PostMapping("/{id}/enable")
    @RequiresAction("core.user.manage")
    public ResponseEntity<Void> enable(@PathVariable("id") UUID id) {
        requireRoleAssignForTenantAdmin(id);
        userDirectoryService.enable(id);
        return ResponseEntity.noContent().build();
    }

    /** The {@code UserInvitationController.create} pattern: a second action only when the change needs it. */
    private void requireRoleAssignForTenantAdmin(UUID id) {
        if (roleService.holdsTenantAdmin(id)) {
            permissionService.require(ROLE_ASSIGN_ACTION);
        }
    }

    @ExceptionHandler(RoleService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(RoleService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(RoleService.AdminGuardException.class)
    public ResponseEntity<ApiErrorResponse> handleAdminGuard(RoleService.AdminGuardException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    /** Keycloak refused or could not be reached; nothing was changed. The detail is logged, not returned. */
    @ExceptionHandler(KeycloakProvisioningException.class)
    public ResponseEntity<ApiErrorResponse> handleKeycloak(KeycloakProvisioningException e) {
        log.warn("Account status change not applied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.of(
                        ApiError.INTERNAL,
                        "The sign-in service did not accept the change. Nothing was changed; try again.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
