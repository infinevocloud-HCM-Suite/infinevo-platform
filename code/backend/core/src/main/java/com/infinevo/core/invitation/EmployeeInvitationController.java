package com.infinevo.core.invitation;

import com.infinevo.shared.authz.RequiresAction;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * <p>Guarded by {@code core.employee.create}.
 */
@RestController
@RequestMapping("/api/v1/employee-invitations")
public class EmployeeInvitationController {

    private final InvitationService invitationService;

    public EmployeeInvitationController(InvitationService invitationService) {
        this.invitationService = Objects.requireNonNull(invitationService, "invitationService must not be null");
    }

    @PostMapping
    @RequiresAction("core.employee.create")
    public ResponseEntity<EmployeeInvitationResponse> create(@RequestBody EmployeeInvitationRequest request) {
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
}
