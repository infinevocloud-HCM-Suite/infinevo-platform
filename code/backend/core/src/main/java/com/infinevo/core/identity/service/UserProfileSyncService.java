package com.infinevo.core.identity.service;

import com.infinevo.core.identity.entity.UserAccount;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Service responsible for synchronizing user profiles from Keycloak OIDC JWT tokens into core.user_account.
 */
public interface UserProfileSyncService {

    UserAccount syncUserProfile(Jwt jwt, UUID tenantId);

    UserAccount getOrSyncUserProfile(Jwt jwt, UUID tenantId);
}
