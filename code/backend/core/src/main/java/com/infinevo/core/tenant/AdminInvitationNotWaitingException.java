package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * Thrown when the platform asks to resend a tenant's administrator invitation and none is waiting: it was
 * accepted, revoked, or never sent (W-73.2). Answered {@code 409}.
 */
public class AdminInvitationNotWaitingException extends RuntimeException {

    public AdminInvitationNotWaitingException(UUID tenantId) {
        super("Tenant " + tenantId + " has no pending or expired administrator invitation to resend");
    }
}
