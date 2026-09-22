package com.infinevo.core.identity.service.impl;

import com.infinevo.core.identity.entity.UserAccount;
import com.infinevo.core.identity.repository.UserAccountRepository;
import com.infinevo.core.identity.service.UserProfileSyncService;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of UserProfileSyncService.
 * Synchronizes Keycloak OIDC claims (sub, email, given_name, family_name) to core.user_account.
 */
@Service
@Transactional
public class UserProfileSyncServiceImpl implements UserProfileSyncService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_GIVEN_NAME = "given_name";
    private static final String CLAIM_FAMILY_NAME = "family_name";

    private final UserAccountRepository userAccountRepository;

    public UserProfileSyncServiceImpl(UserAccountRepository userAccountRepository) {
        this.userAccountRepository =
                Objects.requireNonNull(userAccountRepository, "userAccountRepository must not be null");
    }

    @Override
    public UserAccount syncUserProfile(Jwt jwt, UUID tenantId) {
        Objects.requireNonNull(jwt, "jwt must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("Jwt subject (sub) must not be empty");
        }

        String emailClaim = jwt.getClaimAsString(CLAIM_EMAIL);
        final String email = (emailClaim != null && !emailClaim.isBlank()) ? emailClaim : sub + "@keycloak.local";

        String firstName = jwt.getClaimAsString(CLAIM_GIVEN_NAME);
        String lastName = jwt.getClaimAsString(CLAIM_FAMILY_NAME);

        UserAccount userAccount = userAccountRepository
                .findByKeycloakSub(sub)
                .orElseGet(() -> new UserAccount(tenantId, sub, email, firstName, lastName));

        userAccount.setTenantId(tenantId);
        userAccount.setEmail(email);
        if (firstName != null) {
            userAccount.setFirstName(firstName);
        }
        if (lastName != null) {
            userAccount.setLastName(lastName);
        }
        userAccount.setLastLoginAt(OffsetDateTime.now());

        return userAccountRepository.save(userAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccount getOrSyncUserProfile(Jwt jwt, UUID tenantId) {
        Objects.requireNonNull(jwt, "jwt must not be null");
        String sub = jwt.getSubject();
        return userAccountRepository.findByKeycloakSub(sub).orElseGet(() -> syncUserProfile(jwt, tenantId));
    }
}
