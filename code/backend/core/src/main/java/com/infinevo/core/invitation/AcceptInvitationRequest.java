package com.infinevo.core.invitation;

/**
 * Request to accept an invitation using its single-use bearer token (W-24.2, spec section 4), with the
 * password the invitee chose on the accept page (D-88). The realm's password policy is applied by Keycloak
 * when the password is set; a refusal comes back as a {@code 400} naming the rule.
 */
public record AcceptInvitationRequest(String token, String password) {}
