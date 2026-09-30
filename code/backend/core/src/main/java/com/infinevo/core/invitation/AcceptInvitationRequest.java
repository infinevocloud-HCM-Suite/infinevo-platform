package com.infinevo.core.invitation;

/**
 * Request to accept an invitation using its single-use bearer token (W-24.2, spec section 4).
 */
public record AcceptInvitationRequest(String token) {}
