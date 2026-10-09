package com.infinevo.core.invitation;

/**
 * Keycloak refused the password the invitee chose (D-88). The message is the realm's own wording of the
 * rule that failed — "Invalid password: minimum length 10." — and is safe to show on the accept page.
 * Nothing has been written: the invitation stays {@code PENDING} and the invitee tries again.
 */
public class PasswordPolicyException extends RuntimeException {

    public PasswordPolicyException(String message) {
        super(message);
    }
}
