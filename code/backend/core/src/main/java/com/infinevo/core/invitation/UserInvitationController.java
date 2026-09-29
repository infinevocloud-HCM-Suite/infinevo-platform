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
 * Controller for user invitations ({@code /api/v1/user-invitations}) (W-24.2, spec section 4).
 *
 * <p>Guarded by {@code core.user.manage}.
 */
@RestController
@RequestMapping("/api/v1/user-invitations")
public class UserInvitationController {

    private final InvitationService invitationService;

    public UserInvitationController(InvitationService invitationService) {
        this.invitationService = Objects.requireNonNull(invitationService, "invitationService must not be null");
    }

    @PostMapping
    @RequiresAction("core.user.manage")
    public ResponseEntity<UserInvitationResponse> create(@RequestBody UserInvitationRequest request) {
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
}
