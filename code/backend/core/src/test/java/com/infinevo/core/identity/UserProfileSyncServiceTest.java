package com.infinevo.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.identity.entity.UserAccount;
import com.infinevo.core.identity.repository.UserAccountRepository;
import com.infinevo.core.identity.service.impl.UserProfileSyncServiceImpl;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class UserProfileSyncServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private UserProfileSyncServiceImpl userProfileSyncService;

    @Test
    @DisplayName("Synchronizes new Keycloak user identity into core.user_account")
    void syncsNewUserIdentity() {
        UUID tenantId = UUID.randomUUID();
        String sub = UUID.randomUUID().toString();
        Jwt jwt = new Jwt(
                "token-val",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", sub,
                        "email", "alice@example.com",
                        "given_name", "Alice",
                        "family_name", "Smith"));

        when(userAccountRepository.findByKeycloakSub(sub)).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAccount result = userProfileSyncService.syncUserProfile(jwt, tenantId);

        assertThat(result).isNotNull();
        assertThat(result.getKeycloakSub()).isEqualTo(sub);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getLastName()).isEqualTo("Smith");
        assertThat(result.getTenantId()).isEqualTo(tenantId);

        verify(userAccountRepository).save(any(UserAccount.class));
    }
}
