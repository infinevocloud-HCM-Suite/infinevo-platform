package com.infinevo.core.invitation;

import java.util.Set;
import java.util.UUID;

/**
 * Request to invite a company user (W-24.2).
 */
public record UserInvitationRequest(String email, Set<UUID> roleIds) {}
