package com.infinevo.core.invitation;

/**
 * The lifecycle status of a user or employee invitation (W-24.2, spec section 2).
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,
    EXPIRED
}
