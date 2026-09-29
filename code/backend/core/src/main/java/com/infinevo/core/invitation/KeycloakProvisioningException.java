package com.infinevo.core.invitation;

/**
 * Thrown when provisioning a user in Keycloak fails (W-24.2).
 */
public class KeycloakProvisioningException extends RuntimeException {

    public KeycloakProvisioningException(String message) {
        super(message);
    }

    public KeycloakProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
