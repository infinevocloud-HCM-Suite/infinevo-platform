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

    private UUID tenant;
    private UUID adminUserId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("RoleJoin " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

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
}
