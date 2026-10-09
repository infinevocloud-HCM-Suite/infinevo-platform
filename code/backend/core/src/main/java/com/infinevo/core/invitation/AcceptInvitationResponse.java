package com.infinevo.core.invitation;

/**
 * The reply to {@code POST /api/v1/invitations/accept}: the message and what the invitee does next (D-62).
 * {@code outcome} is null only if a caller ever returns none; the accept page then shows its general text.
 */
public record AcceptInvitationResponse(String message, AcceptOutcome outcome) {}
