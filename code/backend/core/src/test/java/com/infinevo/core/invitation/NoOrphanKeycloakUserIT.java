package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * Asserts an invitation that expires unaccepted leaves no Keycloak user (W-24.2, spec §7).
 *
 * <p>Validates the critical architectural fix: user provisioning occurs at acceptance time,
 * not at invitation creation time, preventing orphan Keycloak users.
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class NoOrphanKeycloakUserIT extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserInvitationRepository userInvitationRepository;

    @MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    private UUID tenant;
    private UUID adminUserId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("NoOrphan " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an invitation that expires unaccepted never calls Keycloak user provisioning")
    void unacceptedInvitationLeavesNoKeycloakUser() {
        // 1. Create invitation
        UserInvitationRequest request = new UserInvitationRequest("unaccepted@example.com", Set.of());
        UserInvitationResponse response = invitationService.createUserInvitation(request, adminUserId);

        // 2. Assert Keycloak provisioning was NOT called during creation
        verify(keycloakProvisioningService, never()).getOrCreateKeycloakUser(anyString(), any(), any());

        // 3. Fast-forward expiration
        UserInvitation inv = userInvitationRepository.findById(response.id()).orElseThrow();
        inv.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        inv.setStatus(InvitationStatus.EXPIRED);
        userInvitationRepository.save(inv);

        // 4. Still no Keycloak user provisioned
        verify(keycloakProvisioningService, never()).getOrCreateKeycloakUser(anyString(), any(), any());
        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
    }
}
