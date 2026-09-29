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
     * Finds an existing Keycloak user ID by email, or creates a new Keycloak user with that email.
     *
     * @param email the user's email address
     * @param firstName optional first name
     * @param lastName optional last name
     * @return the Keycloak user UUID
     */
    UUID getOrCreateKeycloakUser(String email, String firstName, String lastName);

    record ProvisioningResult(UUID keycloakUserId, boolean newlyCreated) {}

    default ProvisioningResult getOrCreateKeycloakUserWithStatus(String email, String firstName, String lastName) {
        return new ProvisioningResult(getOrCreateKeycloakUser(email, firstName, lastName), false);
    }

    /**
     * Deletes a Keycloak user by their UUID during compensating cleanup if subsequent steps fail.
     */
    default void deleteKeycloakUser(UUID keycloakUserId) {}
}
