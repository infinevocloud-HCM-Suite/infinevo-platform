package com.infinevo.core.invitation;

/**
 * Thrown when an invitation token is presented after its expiry (W-24.2).
 *
 * <p>The accepting or declining transaction marks the row {@code EXPIRED} before throwing this, and
 * {@link InvitationServiceImpl} declares it {@code noRollbackFor}, so that status change commits in the
 * same transaction that holds the row lock. A second transaction updating the locked row — the earlier
 * design — waited on the first forever.
 */
public class InvitationExpiredException extends IllegalStateException {

    public InvitationExpiredException() {
        super("Invitation has expired");
    }
}
