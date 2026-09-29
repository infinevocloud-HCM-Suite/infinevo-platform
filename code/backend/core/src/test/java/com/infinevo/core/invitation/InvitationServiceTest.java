package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.authz.RoleService;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link InvitationServiceImpl} (W-24.2, spec section 7).
 *
 * <p>Covers:
 * <ul>
 *   <li>Single-use token enforcement</li>
 *   <li>Refusal of expired and revoked tokens</li>
 *   <li>Resend invalidation and superseding of previous invitations</li>
 *   <li>Creation validation and duplicate active invitation rejection</li>
 * </ul>
 */
class InvitationServiceTest {

    private UserInvitationRepository userInvitationRepository;
    private UserInvitationRoleRepository userInvitationRoleRepository;
    private EmployeeInvitationRepository employeeInvitationRepository;
    private RoleRepository roleRepository;
    private RoleService roleService;
    private EmployeeRepository employeeRepository;
    private UserAccountRepository userAccountRepository;
    private UserProfileSyncService userProfileSyncService;
    private KeycloakProvisioningService keycloakProvisioningService;
    private JdbcTemplate jdbcTemplate;
    private NotificationService notificationService;

    private InvitationServiceImpl invitationService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userInvitationRepository = mock(UserInvitationRepository.class);
        userInvitationRoleRepository = mock(UserInvitationRoleRepository.class);
        employeeInvitationRepository = mock(EmployeeInvitationRepository.class);
        roleRepository = mock(RoleRepository.class);
        roleService = mock(RoleService.class);
        employeeRepository = mock(EmployeeRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        userProfileSyncService = mock(UserProfileSyncService.class);
        keycloakProvisioningService = mock(KeycloakProvisioningService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        notificationService = mock(NotificationService.class);

        invitationService = new InvitationServiceImpl(
                userInvitationRepository,
                userInvitationRoleRepository,
                employeeInvitationRepository,
                roleRepository,
                roleService,
                employeeRepository,
                userAccountRepository,
                userProfileSyncService,
                keycloakProvisioningService,
                jdbcTemplate);

        // Inject optional notification service via reflection or setter
        try {
            var field = InvitationServiceImpl.class.getDeclaredField("notificationService");
            field.setAccessible(true);
            field.set(invitationService, notificationService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(userInvitationRepository.save(any(UserInvitation.class))).thenAnswer(invocation -> {
            UserInvitation inv = invocation.getArgument(0);
            if (inv.getId() == null) {
                setId(inv, UUID.randomUUID());
            }
            return inv;
        });

        when(employeeInvitationRepository.save(any(EmployeeInvitation.class))).thenAnswer(invocation -> {
            EmployeeInvitation inv = invocation.getArgument(0);
            if (inv.getId() == null) {
                setId(inv, UUID.randomUUID());
            }
            return inv;
        });

        TenantContext.set(tenantId);
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createUserInvitation: generates 256-bit token hash, persists invitation and roles, notifies invitee")
    void createUserInvitationSuccess() {
        UUID roleId = UUID.randomUUID();
        Role role = mock(Role.class);
        when(roleRepository.findByIdAndTenantId(roleId, tenantId)).thenReturn(Optional.of(role));
        when(userInvitationRepository.findActivePendingByEmail(
                        eq(tenantId), eq("user@example.com"), any(Instant.class)))
                .thenReturn(Optional.empty());

        when(userInvitationRepository.save(any(UserInvitation.class))).thenAnswer(invocation -> {
            UserInvitation inv = invocation.getArgument(0);
            if (inv.getId() == null) {
                setId(inv, UUID.randomUUID());
            }
            return inv;
        });

        UserInvitationRequest request = new UserInvitationRequest("User@Example.com", Set.of(roleId));
        UserInvitationResponse response = invitationService.createUserInvitation(request, actorUserId);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(response.roleIds()).containsExactly(roleId);

        verify(userInvitationRepository).save(any(UserInvitation.class));
        verify(userInvitationRoleRepository).save(any(UserInvitationRole.class));
        verify(notificationService).compose(eq(NotificationEvent.USER_INVITATION), any(), any());
    }

    @Test
    @DisplayName("createUserInvitation: duplicate active pending invitation for email is refused")
    void createUserInvitationDuplicateRefused() {
        when(userInvitationRepository.findActivePendingByEmail(
                        eq(tenantId), eq("user@example.com"), any(Instant.class)))
                .thenReturn(Optional.of(mock(UserInvitation.class)));

        UserInvitationRequest request = new UserInvitationRequest("user@example.com", Set.of());
        assertThatThrownBy(() -> invitationService.createUserInvitation(request, actorUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("createEmployeeInvitation: employee with no work email is refused")
    void createEmployeeInvitationNoWorkEmailRefused() {
        UUID empId = UUID.randomUUID();
        Employee emp = mock(Employee.class);
        when(emp.getWorkEmail()).thenReturn(null);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(empId, tenantId))
                .thenReturn(Optional.of(emp));

        EmployeeInvitationRequest request = new EmployeeInvitationRequest(empId);
        assertThatThrownBy(() -> invitationService.createEmployeeInvitation(request, actorUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no work email");
    }

    @Test
    @DisplayName("resendUserInvitation: revokes previous invitation, links superseded_by, issues new token")
    void resendUserInvitationSuccess() {
        UUID oldId = UUID.randomUUID();
        UserInvitation oldInv = new UserInvitation(
                tenantId,
                "user@example.com",
                InvitationTokenUtils.hashToken("oldtoken"),
                Instant.now().plus(5, ChronoUnit.DAYS),
                actorUserId,
                "system");
        setId(oldInv, oldId);

        when(userInvitationRepository.findByIdAndTenantId(oldId, tenantId)).thenReturn(Optional.of(oldInv));
        when(userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantId, oldInv.getId()))
                .thenReturn(List.of());
        when(userInvitationRepository.save(any(UserInvitation.class))).thenAnswer(inv -> {
            UserInvitation u = inv.getArgument(0);
            if (u.getId() == null) {
                setId(u, UUID.randomUUID());
            }
            return u;
        });

        UserInvitationResponse response = invitationService.resendUserInvitation(oldId, actorUserId);

        assertThat(oldInv.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        assertThat(oldInv.getRevokedAt()).isNotNull();
        assertThat(oldInv.getSupersededById()).isNotNull();
        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
        verify(notificationService).compose(eq(NotificationEvent.USER_INVITATION), any(), any());
    }

    @Test
    @DisplayName("acceptInvitation: token is single use — second acceptance is refused")
    void acceptInvitationSingleUse() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId, "user@example.com", hash, Instant.now().plus(2, ChronoUnit.DAYS), actorUserId, "system");
        setId(inv, UUID.randomUUID());

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(anyString(), any(), any()))
                .thenReturn(keycloakUserId);

        UserAccount userAccount = mock(UserAccount.class);
        when(userAccount.getId()).thenReturn(UUID.randomUUID());
        when(userAccountRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(Optional.of(userAccount));
        when(userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantId, inv.getId()))
                .thenReturn(List.of());

        // First acceptance succeeds
        invitationService.acceptInvitation(token);
        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(inv.getAcceptedAt()).isNotNull();

        // Second acceptance throws IllegalStateException
        assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been accepted");
    }

    @Test
    @DisplayName("acceptInvitation: expired token is refused and marked EXPIRED")
    void acceptInvitationExpiredRefused() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId, "user@example.com", hash, Instant.now().minus(1, ChronoUnit.DAYS), actorUserId, "system");

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");

        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(keycloakProvisioningService, never()).getOrCreateKeycloakUser(anyString(), any(), any());
    }

    @Test
    @DisplayName("acceptInvitation: revoked token is refused")
    void acceptInvitationRevokedRefused() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId, "user@example.com", hash, Instant.now().plus(1, ChronoUnit.DAYS), actorUserId, "system");
        inv.setStatus(InvitationStatus.REVOKED);

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revoked");

        verify(keycloakProvisioningService, never()).getOrCreateKeycloakUser(anyString(), any(), any());
    }

    @Test
    @DisplayName("revokeUserInvitation: marks invitation REVOKED with timestamp")
    void revokeUserInvitationSuccess() {
        UUID id = UUID.randomUUID();
        UserInvitation inv = new UserInvitation(
                tenantId,
                "user@example.com",
                "somehash",
                Instant.now().plus(2, ChronoUnit.DAYS),
                actorUserId,
                "system");
        setId(inv, id);
        when(userInvitationRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(inv));

        invitationService.revokeUserInvitation(id, actorUserId);

        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        assertThat(inv.getRevokedAt()).isNotNull();
        verify(userInvitationRepository).save(inv);
    }
}
