package com.infinevo.core.invitation;

import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for public invitation acceptance and decline endpoints (W-24.2, spec section 4).
 *
 * <p>Unauthenticated endpoints: the bearer token is the single-use credential.
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationAcceptanceController {

    private final InvitationService invitationService;

    public InvitationAcceptanceController(InvitationService invitationService) {
        this.invitationService = Objects.requireNonNull(invitationService, "invitationService must not be null");
    }

    @PostMapping("/accept")
    public ResponseEntity<InvitationMessageResponse> accept(@RequestBody AcceptInvitationRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        invitationService.acceptInvitation(request.token());
        return ResponseEntity.ok(new InvitationMessageResponse("Invitation accepted successfully"));
    }

    @PostMapping("/decline")
    public ResponseEntity<InvitationMessageResponse> decline(@RequestBody DeclineInvitationRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        invitationService.declineInvitation(request.token(), request.reason());
        return ResponseEntity.ok(new InvitationMessageResponse("Invitation declined successfully"));
    }
}
