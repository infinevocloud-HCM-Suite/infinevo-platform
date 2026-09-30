package com.infinevo.core.invitation;

import java.util.UUID;

/**
 * Provisions users in Keycloak at invitation acceptance time (W-24.2, spec section 4).
 *
 * <p>Keycloak user creation happens at acceptance, not at invitation creation. This prevents
 * unaccepted invitations from leaving live accounts in the realm.
 */
public interface KeycloakProvisioningService {

    /** The Keycloak user, and whether this call created it — only a created user is compensated. */
    record ProvisioningResult(UUID keycloakUserId, boolean newlyCreated) {}

    /**
     * Finds an existing Keycloak user by email, or creates one that must set a password on first login
     * and is sent Keycloak's set-password email.
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
}
