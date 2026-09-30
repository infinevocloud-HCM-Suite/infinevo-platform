package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Asserts the employee invitation lifecycle (W-24.2, spec §7):
 * <ul>
 *   <li>List filters by status and employee</li>
 *   <li>Resend supersedes and invalidates the previous invitation</li>
 *   <li>Revoke transitions state to REVOKED and invalidates token</li>
 * </ul>
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class EmployeeInvitationLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private EmployeeService employeeService;

    private UUID tenant;
    private UUID adminUserId;
    private UUID employeeId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("EmpLifecycle " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        TenantContext.set(tenant);

        // Seed an employee
        com.infinevo.core.employee.EmployeeResponse employee =
                employeeService.create(new com.infinevo.core.employee.EmployeeRequest(
                        "EMP-" + UUID.randomUUID().toString().substring(0, 8),
                        "Jane",
                        null,
                        "Doe",
                        "FEMALE",
                        LocalDate.of(2024, 1, 1),
                        null,
                        com.infinevo.core.employee.EmploymentStatus.ACTIVE,
                        "jane.doe." + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                        "9876543210",
                        true,
                        null,
                        null,
                        null));
        employeeId = employee.id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("employee invitation lifecycle: create, list with filters, resend, revoke")
    void fullLifecycle() {
        // 1. Create invitation
        EmployeeInvitationResponse created =
                invitationService.createEmployeeInvitation(new EmployeeInvitationRequest(employeeId), adminUserId);
        assertThat(created.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(created.employeeId()).isEqualTo(employeeId);

        // 2. List filters by status and employee
        List<EmployeeInvitationResponse> pendingList =
                invitationService.listEmployeeInvitations(InvitationStatus.PENDING, employeeId);
        assertThat(pendingList).hasSize(1);
        assertThat(pendingList.get(0).id()).isEqualTo(created.id());

        List<EmployeeInvitationResponse> acceptedList =
                invitationService.listEmployeeInvitations(InvitationStatus.ACCEPTED, employeeId);
        assertThat(acceptedList).isEmpty();

        // 3. Resend supersedes and invalidates previous
        EmployeeInvitationResponse resent = invitationService.resendEmployeeInvitation(created.id(), adminUserId);
        assertThat(resent.id()).isNotEqualTo(created.id());
        assertThat(resent.status()).isEqualTo(InvitationStatus.PENDING);

        // Verify old is REVOKED
        List<EmployeeInvitationResponse> revokedList =
                invitationService.listEmployeeInvitations(InvitationStatus.REVOKED, employeeId);
        assertThat(revokedList).hasSize(1);
        assertThat(revokedList.get(0).id()).isEqualTo(created.id());
        assertThat(revokedList.get(0).supersededById()).isEqualTo(resent.id());

        // 4. Revoke is a state
        invitationService.revokeEmployeeInvitation(resent.id(), adminUserId);
        List<EmployeeInvitationResponse> finalList =
                invitationService.listEmployeeInvitations(InvitationStatus.REVOKED, employeeId);
        assertThat(finalList).hasSize(2);
    }

    @Autowired
    private EmployeeInvitationRepository employeeInvitationRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    @Test
    @DisplayName("after resend, and after revoke, the old token is refused and nothing is provisioned")
    void oldTokenRefusedAfterResendAndRevoke() {
        // Resend: the superseded token no longer works
        String resentToken = InvitationTokenUtils.generateToken();
        EmployeeInvitation original = employeeInvitationRepository.save(new EmployeeInvitation(
                tenant,
                employeeId,
                "resend-" + UUID.randomUUID() + "@example.com",
                InvitationTokenUtils.hashToken(resentToken),
                java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS),
                adminUserId,
                "admin"));
        EmployeeInvitationResponse replacement =
                invitationService.resendEmployeeInvitation(original.getId(), adminUserId);

        TenantContext.clear();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invitationService.acceptInvitation(resentToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revoked");

        // Revoke: the revoked token no longer works
        TenantContext.set(tenant);
        invitationService.revokeEmployeeInvitation(replacement.id(), adminUserId);
        String revokedToken = InvitationTokenUtils.generateToken();
        EmployeeInvitation another = employeeInvitationRepository.save(new EmployeeInvitation(
                tenant,
                employeeId,
                "revoke-" + UUID.randomUUID() + "@example.com",
                InvitationTokenUtils.hashToken(revokedToken),
                java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS),
                adminUserId,
                "admin"));
        invitationService.revokeEmployeeInvitation(another.getId(), adminUserId);

        TenantContext.clear();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invitationService.acceptInvitation(revokedToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revoked");

        org.mockito.Mockito.verify(keycloakProvisioningService, org.mockito.Mockito.never())
                .getOrCreateKeycloakUser(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }
}
