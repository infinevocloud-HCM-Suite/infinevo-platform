package com.infinevo.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    @DisplayName("Converts valid Keycloak OIDC JWT token into JwtAuthenticationToken with extracted realm roles")
    void convertsJwtWithRealmRoles() {
        Jwt jwt = new Jwt(
                "token-val",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "11111111-1111-1111-1111-111111111111",
                        "preferred_username", "johndoe",
                        "email", "john@example.com",
                        "realm_access", Map.of("roles", List.of("admin", "ROLE_HR_MANAGER"))));

        AbstractAuthenticationToken authToken = converter.convert(jwt);

        assertThat(authToken).isNotNull();
        assertThat(authToken.getName()).isEqualTo("11111111-1111-1111-1111-111111111111");

        List<String> authorities = authToken.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains("ROLE_ADMIN", "ROLE_HR_MANAGER");
    }
}
