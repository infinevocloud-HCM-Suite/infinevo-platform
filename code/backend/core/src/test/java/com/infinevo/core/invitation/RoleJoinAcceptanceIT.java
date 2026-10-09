package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.authz.AuthzTestSchema;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asserts invitation role assignment rules (W-24.2, spec §7):
 * <ul>
 *   <li>An invitation naming a nonexistent role is refused at create, not at acceptance</li>
 *   <li>An invitation with roles produces the exact user_role rows on acceptance</li>
 * </ul>
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class RoleJoinAcceptanceIT extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private UUID tenant;
    private UUID adminUserId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("RoleJoin " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @org.springframework.boot.test.mock.mockito.MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    @Autowired
    private UserInvitationRepository userInvitationRepository;

    @Autowired
    private UserInvitationRoleRepository userInvitationRoleRepository;

    @Autowired
    private com.infinevo.shared.identity.UserAccountRepository userAccountRepository;

    @Autowired
    private com.infinevo.core.authz.UserRoleRepository userRoleRepository;

    @Test
    @DisplayName("an invitation naming a role that does not exist is refused at create, not at acceptance")
    void nonExistentRoleRefusedAtCreate() {
        UUID nonexistentRole = UUID.randomUUID();
        UserInvitationRequest request = new UserInvitationRequest("test@example.com", Set.of(nonexistentRole));

        assertThatThrownBy(() -> invitationService.createUserInvitation(request, adminUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found in tenant");
    }

    @Test
    @DisplayName("creating an invitation with valid roles persists role mappings")
    void validRolesPersisted() throws SQLException {
        UUID hrRole = AuthzTestSchema.roleId(tenant, "hr");
        UUID employeeRole = AuthzTestSchema.roleId(tenant, "employee");

        UserInvitationRequest request =
                new UserInvitationRequest("two-roles@example.com", Set.of(hrRole, employeeRole));
        UserInvitationResponse response = invitationService.createUserInvitation(request, adminUserId);

        assertThat(response).isNotNull();
        assertThat(response.roleIds()).containsExactlyInAnyOrder(hrRole, employeeRole);
    }

    @Test
    @DisplayName("an invitation with roles produces the exact user_role rows on acceptance")
    void invitationWithRolesProducesUserRolesOnAcceptance() throws SQLException {
        UUID hrRole = AuthzTestSchema.roleId(tenant, "hr");
        UUID employeeRole = AuthzTestSchema.roleId(tenant, "employee");
        UUID keycloakUserId = UUID.randomUUID();

        org.mockito.Mockito.when(keycloakProvisioningService.getOrCreateKeycloakUser(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, true));

        String testToken = InvitationTokenUtils.generateToken();
        String testTokenHash = InvitationTokenUtils.hashToken(testToken);

        UserInvitation inv = new UserInvitation(
                tenant,
                "role-join-test@example.com",
                testTokenHash,
                java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS),
                adminUserId,
                "admin");
        UserInvitation saved = userInvitationRepository.save(inv);
        userInvitationRoleRepository.save(new UserInvitationRole(tenant, saved.getId(), hrRole, "admin"));
        userInvitationRoleRepository.save(new UserInvitationRole(tenant, saved.getId(), employeeRole, "admin"));

        TenantContext.clear();
        invitationService.acceptInvitation(testToken, "Str0ng-Passw0rd!");

        TenantContext.set(tenant);
        com.infinevo.shared.identity.UserAccount account = transactionTemplate.execute(status -> userAccountRepository
                .findByTenantIdAndKeycloakUserId(tenant, keycloakUserId)
                .orElseThrow());
        java.util.List<com.infinevo.core.authz.UserRole> assignedRoles = transactionTemplate.execute(
                status -> userRoleRepository.findByTenantIdAndUserAccountId(tenant, account.getId()));
        assertThat(assignedRoles.stream().map(com.infinevo.core.authz.UserRole::getRoleId))
                .containsExactlyInAnyOrder(hrRole, employeeRole);
    }

    @Test
    @DisplayName("an account that already holds a role keeps it when it accepts an invitation carrying another")
    void acceptanceAddsToExistingRoles() throws SQLException {
        UUID heldRole = AuthzTestSchema.roleId(tenant, "hr");
        UUID invitedRole = AuthzTestSchema.roleId(tenant, "manager");
        UUID keycloakUserId = UUID.randomUUID();
        UUID accountId = AuthzTestSchema.insertMember(tenant, keycloakUserId, "already-member@example.com");
        AuthzTestSchema.grant(tenant, accountId, heldRole);

        org.mockito.Mockito.when(keycloakProvisioningService.getOrCreateKeycloakUser(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, false));

        String token = InvitationTokenUtils.generateToken();
        UserInvitation saved = userInvitationRepository.save(new UserInvitation(
                tenant,
                "already-member@example.com",
                InvitationTokenUtils.hashToken(token),
                java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS),
                adminUserId,
                "admin"));
        userInvitationRoleRepository.save(new UserInvitationRole(tenant, saved.getId(), invitedRole, "admin"));

        TenantContext.clear();
        invitationService.acceptInvitation(token, "Str0ng-Passw0rd!");

        TenantContext.set(tenant);
        java.util.List<com.infinevo.core.authz.UserRole> roles = transactionTemplate.execute(
                status -> userRoleRepository.findByTenantIdAndUserAccountId(tenant, accountId));
        assertThat(roles.stream().map(com.infinevo.core.authz.UserRole::getRoleId))
                .containsExactlyInAnyOrder(heldRole, invitedRole);
    }
}
