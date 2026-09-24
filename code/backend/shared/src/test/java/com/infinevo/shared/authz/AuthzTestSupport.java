package com.infinevo.shared.authz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Binds a caller the way the filters do: an authenticated {@link Jwt} whose {@code sub} is the user. */
final class AuthzTestSupport {

    private AuthzTestSupport() {}

    static void authenticate(UUID keycloakUserId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(keycloakUserId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    /**
     * A resolver where the profile row id is derived from the subject, so a test can grant to
     * "the user" without a database. Identity, deliberately: one user, one row, per tenant.
     */
    static UserAccountIdResolver identityResolver() {
        return (tenantId, keycloakUserId) -> Optional.of(keycloakUserId);
    }
}
