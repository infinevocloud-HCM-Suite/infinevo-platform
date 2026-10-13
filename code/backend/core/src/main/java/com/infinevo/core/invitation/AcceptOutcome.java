package com.infinevo.core.invitation;

/**
 * What the invitee should do after accepting (D-62, D-88): the accept page words its message from this.
 * The password is chosen on the accept page itself and set in Keycloak by the accept call, so there is
 * no second mail and nobody is sent to the sign-in screen without a password.
 */
public enum AcceptOutcome {
    /** The password the invitee chose is set; they sign in with it now. */
    PASSWORD_SET,
    /** The invitee already had a password from an earlier invitation; it is kept and they sign in with it. */
    EXISTING_ACCOUNT
}
