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
     * invitee must do next (D-62).
     */
    record ProvisioningResult(UUID keycloakUserId, boolean newlyCreated, AcceptOutcome outcome) {

        /** A created user was sent the set-password mail; a reused one signs in with its password. */
        public ProvisioningResult(UUID keycloakUserId, boolean newlyCreated) {
            this(
                    keycloakUserId,
                    newlyCreated,
                    newlyCreated ? AcceptOutcome.SET_PASSWORD_EMAIL_SENT : AcceptOutcome.EXISTING_ACCOUNT);
        }
    }

    /**
     * Finds an existing Keycloak user by email, or creates one that must set a password on first login
     * and is sent Keycloak's set-password email. An existing user who never set a password (an earlier
     * acceptance whose mail was lost) is sent the mail again (D-62).
     *
     * @param email the user's email address
     * @param firstName optional first name
     * @param lastName optional last name
     * @return the Keycloak user and whether it was created by this call
     * @throws KeycloakProvisioningException if Keycloak is not configured or refuses
     */
    ProvisioningResult getOrCreateKeycloakUser(String email, String firstName, String lastName);

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
