package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
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
 * Asserts multi-tenant row-level security isolation on invitation tables (W-24.2, spec §7):
 * <ul>
 *   <li>Tenant A cannot list or revoke Tenant B's invitations</li>
 *   <li>Tenant A cannot read Tenant B's user_invitation_role rows</li>
 *   <li>The lookup function returns only the exact-hash match</li>
 * </ul>
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class InvitationRlsIT extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserInvitationRepository userInvitationRepository;

    @Autowired
    private UserInvitationRoleRepository userInvitationRoleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userOfA;
    private UUID userOfB;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = AuthzTestSchema.insertTenant("TenantA " + UUID.randomUUID());
        tenantB = AuthzTestSchema.insertTenant("TenantB " + UUID.randomUUID());
        userOfA = UUID.randomUUID();
        userOfB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("tenant A cannot list or revoke tenant B's invitations")
    void tenantIsolationOnInvitations() {
        // Create invitation in tenant B
        TenantContext.set(tenantB);
        UserInvitationResponse invB = invitationService.createUserInvitation(
                new UserInvitationRequest("userb@example.com", Set.of()), userOfB);

        // Switch to tenant A
        TenantContext.set(tenantA);

        // Listing in tenant A should return empty
        List<UserInvitationResponse> listA = invitationService.listUserInvitations(null);
        assertThat(listA).isEmpty();

        // Attempting to revoke tenant B's invitation from tenant A fails
        assertThatThrownBy(() -> invitationService.revokeUserInvitation(invB.id(), userOfA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invitation not found");
    }

    @Test
    @DisplayName("security definer lookup returns only exact hash match and nothing else")
    void securityDefinerExactMatchOnly() {
        TenantContext.set(tenantA);
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantA,
                "target@example.com",
                hash,
                java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS),
                userOfA,
                "system");
        userInvitationRepository.save(inv);

        // Clear tenant context to simulate unauthenticated call
        TenantContext.clear();

        // Lookup with exact hash succeeds
        Optional<UserInvitation> found = userInvitationRepository.findByTokenHashSecurityDefiner(hash);
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("target@example.com");

        // Lookup with wrong hash returns empty
        String wrongHash = InvitationTokenUtils.hashToken("wrong-token");
        Optional<UserInvitation> notFound = userInvitationRepository.findByTokenHashSecurityDefiner(wrongHash);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("tenant A cannot read tenant B's user_invitation_role rows")
    void tenantACannotReadTenantBRoleRows() throws SQLException {
        UUID roleOfB = AuthzTestSchema.roleId(tenantB, "hr");
        TenantContext.set(tenantB);
        UserInvitationResponse invB = invitationService.createUserInvitation(
                new UserInvitationRequest("roles-b@example.com", Set.of(roleOfB)), userOfB);

        // Positive control: tenant B sees its own row
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<UserInvitationRole> seenByB =
                tx.execute(status -> userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantB, invB.id()));
        assertThat(seenByB).extracting(UserInvitationRole::getRoleId).containsExactly(roleOfB);

        // Tenant A, even naming B's tenant and invitation explicitly, sees nothing
        TenantContext.set(tenantA);
        List<UserInvitationRole> seenByA =
                tx.execute(status -> userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantB, invB.id()));
        assertThat(seenByA).isEmpty();
        List<UserInvitationRole> allSeenByA = tx.execute(status -> userInvitationRoleRepository.findAll());
        assertThat(allSeenByA).noneMatch(row -> tenantB.equals(row.getTenantId()));
    }
}
