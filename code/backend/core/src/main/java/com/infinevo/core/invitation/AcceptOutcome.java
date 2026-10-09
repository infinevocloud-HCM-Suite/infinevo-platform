package com.infinevo.core.invitation;

/**
 * What the invitee should do after accepting (D-62): the accept page words its message from this, so
 * nobody is sent to the sign-in screen before they have a password, or told to wait for a mail that
 * will not come.
 */
public enum AcceptOutcome {

    /** Keycloak was asked to email the set-password link — a second mail is on its way. */
    SET_PASSWORD_EMAIL_SENT,

    /** The account is ready but Keycloak did not send the set-password mail; "Forgot password?" recovers. */
    SET_PASSWORD_EMAIL_FAILED,

    /** The invitee already has a password from an earlier invitation and signs in with it. */
    EXISTING_ACCOUNT
}
