package com.infinevo.core.invitation;

import java.util.UUID;

/**
 * Provisions users in Keycloak at invitation acceptance time (W-24.2, spec section 4).
 *
 * <p>Keycloak user creation happens at acceptance, not at invitation creation. This prevents
 * unaccepted invitations from leaving live accounts in the realm.
 */
public interface KeycloakProvisioningService {

    /**
     * The Keycloak user, whether this call created it — only a created user is compensated — and what the
     * invitee does next (D-62, D-88).
     */
    record ProvisioningResult(UUID keycloakUserId, boolean newlyCreated, AcceptOutcome outcome) {

        /** A created user has the chosen password set; a reused one signs in with the password it had. */
        public ProvisioningResult(UUID keycloakUserId, boolean newlyCreated) {
            this(
                    keycloakUserId,
                    newlyCreated,
                    newlyCreated ? AcceptOutcome.PASSWORD_SET : AcceptOutcome.EXISTING_ACCOUNT);
        }
    }

    /**
     * Finds an existing Keycloak user by email, or creates one, and sets {@code password} on it (D-88). An
     * existing user who already has a password keeps it — the invitation proves the address, not the right
     * to replace a password — and the result says so; one who never set a password (an earlier acceptance
     * that did not finish) gets this one.
     *
     * @param email the user's email address
     * @param firstName optional first name
     * @param lastName optional last name
     * @param password the password the invitee chose; Keycloak applies the realm policy
     * @return the Keycloak user, whether it was created by this call, and what the invitee does next
     * @throws PasswordPolicyException if Keycloak refuses the password; nothing is left behind
     * @throws KeycloakProvisioningException if Keycloak is not configured or refuses
     */
    ProvisioningResult getOrCreateKeycloakUser(String email, String firstName, String lastName, String password);

    /**
     * Deletes a Keycloak user by their UUID during compensating cleanup if subsequent steps fail.
     */
    void deleteKeycloakUser(UUID keycloakUserId);

    /**
     * Sets the Keycloak user's {@code enabled} flag (W-73.4 Disable / Enable). A user Keycloak no longer
     * holds is logged and ignored: nobody can sign in as them either way.
     *
     * @throws KeycloakProvisioningException if Keycloak is not configured, cannot be reached or refuses
     */
    void setEnabled(UUID keycloakUserId, boolean enabled);
}
