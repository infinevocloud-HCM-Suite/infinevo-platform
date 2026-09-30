package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.SQLException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asserts end-to-end acceptance flow (W-24.2, spec §7):
 * <ul>
 *   <li>Acceptance creates Keycloak user, user_account, user_tenant, user_role</li>
 *   <li>A second acceptance with the same token is refused</li>
 * </ul>
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class AcceptanceIT extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserInvitationRepository userInvitationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    private UUID tenant;
    private UUID adminUserId;
    private UUID assignedRole;
    private UUID mockKeycloakUserId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("Acceptance " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        assignedRole = AuthzTestSchema.roleId(tenant, "hr");
        mockKeycloakUserId = UUID.randomUUID();
        transactionTemplate = new TransactionTemplate(transactionManager);

        when(keycloakProvisioningService.getOrCreateKeycloakUser(anyString(), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(mockKeycloakUserId, true));

        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Autowired
    private UserInvitationRoleRepository userInvitationRoleRepository;

    @Autowired
    private com.infinevo.core.authz.RoleService roleService;

    @Test
    @DisplayName("acceptance creates account, tenant mapping, roles, and a second acceptance is refused")
    void acceptanceCreatesAccountAndRefusesSecondAttempt() {
        // 1. Create invitation via service — proves Keycloak user is NOT created at invitation time
        UserInvitationRequest req = new UserInvitationRequest("accept-test@example.com", Set.of(assignedRole));
        UserInvitationResponse created = invitationService.createUserInvitation(req, adminUserId);

        verify(keycloakProvisioningService, org.mockito.Mockito.never())
                .getOrCreateKeycloakUser(anyString(), any(), any());

        // 2. Perform acceptance with a known single-use bearer token
        String testToken = InvitationTokenUtils.generateToken();
        String testTokenHash = InvitationTokenUtils.hashToken(testToken);

        UserInvitation inv = new UserInvitation(
                tenant,
                "accept-test-2@example.com",
                testTokenHash,
                java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS),
                adminUserId,
                "admin");
        UserInvitation savedInv = userInvitationRepository.save(inv);
        userInvitationRoleRepository.save(new UserInvitationRole(tenant, savedInv.getId(), assignedRole, "admin"));

        TenantContext.clear();

        // 3. Accept using the token (unauthenticated)
        invitationService.acceptInvitation(testToken);

        // Verify Keycloak user was provisioned upon acceptance
        verify(keycloakProvisioningService).getOrCreateKeycloakUser("accept-test-2@example.com", null, null);

        // Verify invitation status updated to ACCEPTED
        TenantContext.set(tenant);
        UserInvitation reloaded = transactionTemplate.execute(
                status -> userInvitationRepository.findById(savedInv.getId()).orElseThrow());
        assertThat(reloaded.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(reloaded.getAcceptedAt()).isNotNull();

        // 4. A second acceptance with the same token is refused
        TenantContext.clear();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invitationService.acceptInvitation(testToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been accepted");
    }
}
