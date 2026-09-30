package com.infinevo.core.invitation;

/**
 * Request to decline an invitation with a stated reason (W-24.2, spec section 4).
 */
public record DeclineInvitationRequest(String token, String reason) {}
