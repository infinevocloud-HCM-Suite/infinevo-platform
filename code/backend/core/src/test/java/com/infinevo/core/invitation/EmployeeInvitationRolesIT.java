package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.authz.UserRole;
import com.infinevo.core.authz.UserRoleRepository;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * An employee invitation carrying extra roles (W-73.3 §7): acceptance grants {@code employee} plus those
 * roles; {@code platform-admin} and another tenant's role are refused at create; the access state reads
 * NONE, INVITED and ACTIVE in turn.
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class EmployeeInvitationRolesIT extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeInvitationRepository employeeInvitationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    private TransactionTemplate transactionTemplate;
    private UUID tenant;
    private UUID adminUserId;
    private UUID employeeId;
    private UUID hrRole;
    private UUID employeeRole;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("EmpRoles " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        hrRole = AuthzTestSchema.roleId(tenant, "hr");
        employeeRole = AuthzTestSchema.roleId(tenant, "employee");
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.set(tenant);
        employeeId = employeeService
                .create(new EmployeeRequest(
                        "EMP-" + UUID.randomUUID().toString().substring(0, 8),
                        "Jane",
                        null,
                        "Doe",
                        "FEMALE",
                        LocalDate.of(2024, 1, 1),
                        null,
                        EmploymentStatus.ACTIVE,
                        "jane.roles." + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                        "9876543210",
                        true,
                        null,
                        null,
                        null))
                .id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("accepting grants employee plus the invitation's roles, and the access state reads ACTIVE")
    void acceptGrantsEmployeePlusGivenRoles() {
        EmployeeInvitationResponse created = invitationService.createEmployeeInvitation(
                new EmployeeInvitationRequest(employeeId, Set.of(hrRole)), adminUserId);
        assertThat(created.roleIds()).containsExactly(hrRole);
        invitationService.revokeEmployeeInvitation(created.id(), adminUserId);

        // The emailed token is never stored, so accept an invitation saved with a known token
        String token = InvitationTokenUtils.generateToken();
        employeeInvitationRepository.save(new EmployeeInvitation(
                tenant,
                employeeId,
                "jane.roles@example.com",
                InvitationTokenUtils.hashToken(token),
                Instant.now().plus(7, ChronoUnit.DAYS),
                adminUserId,
                "admin",
                List.of(hrRole)));
        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(anyString(), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, true));

        TenantContext.clear();
        invitationService.acceptInvitation(token);
        TenantContext.set(tenant);

        UserAccount account = transactionTemplate.execute(status -> userAccountRepository
                .findByTenantIdAndKeycloakUserId(tenant, keycloakUserId)
                .orElseThrow());
        List<UserRole> assigned = transactionTemplate.execute(
                status -> userRoleRepository.findByTenantIdAndUserAccountId(tenant, account.getId()));
        assertThat(assigned.stream().map(UserRole::getRoleId)).containsExactlyInAnyOrder(employeeRole, hrRole);

        EmployeeAccessResponse access = invitationService.employeeAccess(employeeId);
        assertThat(access.state()).isEqualTo(EmployeeAccessResponse.State.ACTIVE);
        assertThat(access.invitationId()).isNull();
        assertThat(access.expiresAt()).isNull();
        assertThat(access.roles().stream().map(EmployeeAccessResponse.RoleRef::code))
                .containsExactly("employee", "hr");
    }

    @Test
    @DisplayName("platform-admin cannot be granted through an employee invitation")
    void platformAdminRefused() throws SQLException {
        UUID platformAdmin = AuthzTestSchema.roleId(tenant, "platform-admin");

        assertThatThrownBy(() -> invitationService.createEmployeeInvitation(
                        new EmployeeInvitationRequest(employeeId, Set.of(platformAdmin)), adminUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform-admin");
        List<EmployeeInvitation> written = transactionTemplate.execute(
                status -> employeeInvitationRepository.findByTenantIdAndEmployeeId(tenant, employeeId));
        assertThat(written).isEmpty();
    }

    @Test
    @DisplayName("another tenant's role id is refused at create")
    void otherTenantsRoleRefused() throws SQLException {
        UUID otherTenant = AuthzTestSchema.insertTenant("EmpRoles other " + UUID.randomUUID());
        UUID otherHr = AuthzTestSchema.roleId(otherTenant, "hr");

        assertThatThrownBy(() -> invitationService.createEmployeeInvitation(
                        new EmployeeInvitationRequest(employeeId, Set.of(otherHr)), adminUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found in tenant");
        List<EmployeeInvitation> written = transactionTemplate.execute(
                status -> employeeInvitationRepository.findByTenantIdAndEmployeeId(tenant, employeeId));
        assertThat(written).isEmpty();
    }

    @Test
    @DisplayName("access reads NONE before any invitation and INVITED, with the roles to come, after one")
    void accessStates() {
        EmployeeAccessResponse none = invitationService.employeeAccess(employeeId);
        assertThat(none.state()).isEqualTo(EmployeeAccessResponse.State.NONE);
        assertThat(none.invitationId()).isNull();
        assertThat(none.expiresAt()).isNull();
        assertThat(none.roles()).isEmpty();

        EmployeeInvitationResponse created = invitationService.createEmployeeInvitation(
                new EmployeeInvitationRequest(employeeId, Set.of(hrRole)), adminUserId);

        EmployeeAccessResponse invited = invitationService.employeeAccess(employeeId);
        assertThat(invited.state()).isEqualTo(EmployeeAccessResponse.State.INVITED);
        assertThat(invited.invitationId()).isEqualTo(created.id());
        assertThat(invited.expiresAt()).isNotNull();
        assertThat(invited.roles().stream().map(EmployeeAccessResponse.RoleRef::code))
                .containsExactly("employee", "hr");
    }

    @Test
    @DisplayName("a resent invitation carries the original's roles")
    void resendCarriesRoles() {
        EmployeeInvitationResponse created = invitationService.createEmployeeInvitation(
                new EmployeeInvitationRequest(employeeId, Set.of(hrRole)), adminUserId);

        EmployeeInvitationResponse resent = invitationService.resendEmployeeInvitation(created.id(), adminUserId);

        assertThat(resent.id()).isNotEqualTo(created.id());
        assertThat(resent.roleIds()).containsExactly(hrRole);
    }

    @Test
    @DisplayName("a role deleted while the invitation is pending is dropped: accept grants employee plus the rest")
    void acceptDropsDeletedRole() throws SQLException {
        UUID doomed = AuthzTestSchema.insertRole(
                tenant, "doomed-" + UUID.randomUUID().toString().substring(0, 8), "Doomed");
        String token = InvitationTokenUtils.generateToken();
        employeeInvitationRepository.save(new EmployeeInvitation(
                tenant,
                employeeId,
                "jane.stale@example.com",
                InvitationTokenUtils.hashToken(token),
                Instant.now().plus(7, ChronoUnit.DAYS),
                adminUserId,
                "admin",
                List.of(hrRole, doomed)));
        deleteRole(doomed);
        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(anyString(), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, true));

        TenantContext.clear();
        invitationService.acceptInvitation(token);
        TenantContext.set(tenant);

        UserAccount account = transactionTemplate.execute(status -> userAccountRepository
                .findByTenantIdAndKeycloakUserId(tenant, keycloakUserId)
                .orElseThrow());
        List<UserRole> assigned = transactionTemplate.execute(
                status -> userRoleRepository.findByTenantIdAndUserAccountId(tenant, account.getId()));
        assertThat(assigned.stream().map(UserRole::getRoleId)).containsExactlyInAnyOrder(employeeRole, hrRole);
    }

    @Test
    @DisplayName("a resent invitation drops a role deleted since the original was sent")
    void resendDropsDeletedRole() throws SQLException {
        UUID doomed = AuthzTestSchema.insertRole(
                tenant, "doomed-" + UUID.randomUUID().toString().substring(0, 8), "Doomed");
        EmployeeInvitationResponse created = invitationService.createEmployeeInvitation(
                new EmployeeInvitationRequest(employeeId, Set.of(hrRole, doomed)), adminUserId);
        deleteRole(doomed);

        EmployeeInvitationResponse resent = invitationService.resendEmployeeInvitation(created.id(), adminUserId);

        assertThat(resent.roleIds()).containsExactly(hrRole);
    }

    private static void deleteRole(UUID roleId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM core.role WHERE id = ?")) {
            ps.setObject(1, roleId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }
    }
}
