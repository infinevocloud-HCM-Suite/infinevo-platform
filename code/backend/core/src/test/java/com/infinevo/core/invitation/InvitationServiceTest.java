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
import com.infinevo.core.authz.UserRole;
import com.infinevo.core.authz.UserRoleRepository;
import com.infinevo.core.authz.UserRolesRequest;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link InvitationServiceImpl} (W-24.2, spec section 7).
 *
 * <p>Covers:
 * <ul>
 *   <li>Single-use token enforcement</li>
 *   <li>Refusal of expired and revoked tokens, with EXPIRED persisted in the same transaction</li>
 *   <li>Resend invalidation and superseding of previous invitations</li>
 *   <li>Creation validation, duplicate active invitation rejection, and no invented inviter</li>
 *   <li>Acceptance adds roles rather than replacing them, and links the employee</li>
 *   <li>The emailed link is the configured frontend page</li>
 * </ul>
 */
class InvitationServiceTest {

    static final String LINK_BASE = "https://app.test/invitations/accept";

    private UserInvitationRepository userInvitationRepository;
    private UserInvitationRoleRepository userInvitationRoleRepository;
    private EmployeeInvitationRepository employeeInvitationRepository;
    private RoleRepository roleRepository;
    private RoleService roleService;
    private UserRoleRepository userRoleRepository;
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
        userRoleRepository = mock(UserRoleRepository.class);
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
                userRoleRepository,
                employeeRepository,
                userAccountRepository,
                userProfileSyncService,
                keycloakProvisioningService,
                jdbcTemplate,
                LINK_BASE);

        // The optional notification service is field-injected
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

    static void setId(Object entity, UUID id) {
        try {
            Class<?> type = entity.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new IllegalStateException("no id field on " + entity.getClass());
        } catch (IllegalAccessException e) {
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
    @DisplayName("createUserInvitation: the email links to the configured frontend page, never the POST API path")
    @SuppressWarnings("unchecked")
    void emailLinkIsTheFrontendAcceptPage() {
        when(userInvitationRepository.findActivePendingByEmail(any(), any(), any()))
                .thenReturn(Optional.empty());

        invitationService.createUserInvitation(new UserInvitationRequest("user@example.com", Set.of()), actorUserId);

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).compose(eq(NotificationEvent.USER_INVITATION), any(), data.capture());
        String link = (String) data.getValue().get("link");
        assertThat(link).startsWith(LINK_BASE + "?token=").doesNotContain("/api/v1/");
        String token = link.substring((LINK_BASE + "?token=").length());
        assertThat(token).matches("[0-9a-f]{64}");

        // The stored hash is the hash of the emailed token, and the token itself is stored nowhere
        ArgumentCaptor<UserInvitation> saved = ArgumentCaptor.forClass(UserInvitation.class);
        verify(userInvitationRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(InvitationTokenUtils.hashToken(token));
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
    @DisplayName("no resolvable actor: create and resend are refused, never stamped with an invented inviter")
    void missingActorIsRefusedNotInvented() {
        assertThatThrownBy(() -> invitationService.createUserInvitation(
                        new UserInvitationRequest("user@example.com", Set.of()), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inviter");
        assertThatThrownBy(() -> invitationService.resendUserInvitation(UUID.randomUUID(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inviter");
        assertThatThrownBy(() -> invitationService.createEmployeeInvitation(
                        new EmployeeInvitationRequest(UUID.randomUUID()), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inviter");
        assertThatThrownBy(() -> invitationService.resendEmployeeInvitation(UUID.randomUUID(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inviter");

        verify(userInvitationRepository, never()).save(any(UserInvitation.class));
        verify(employeeInvitationRepository, never()).save(any(EmployeeInvitation.class));
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
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, true));

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
    @DisplayName("acceptInvitation: adds the invitation's roles to those the account already holds")
    void acceptInvitationAddsRolesRatherThanReplacing() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);
        UserInvitation inv = new UserInvitation(
                tenantId, "user@example.com", hash, Instant.now().plus(2, ChronoUnit.DAYS), actorUserId, "system");
        setId(inv, UUID.randomUUID());
        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(anyString(), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, false));
        UUID accountId = UUID.randomUUID();
        UserAccount account = mock(UserAccount.class);
        when(account.getId()).thenReturn(accountId);
        when(userAccountRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(Optional.of(account));

        UUID heldRole = UUID.randomUUID();
        UUID invitedRole = UUID.randomUUID();
        UserRole held = mock(UserRole.class);
        when(held.getRoleId()).thenReturn(heldRole);
        when(userRoleRepository.findByTenantIdAndUserAccountId(tenantId, accountId))
                .thenReturn(List.of(held));
        UserInvitationRole invited = new UserInvitationRole(tenantId, inv.getId(), invitedRole, "admin");
        when(userInvitationRoleRepository.findByTenantIdAndInvitationId(tenantId, inv.getId()))
                .thenReturn(List.of(invited));

        invitationService.acceptInvitation(token);

        ArgumentCaptor<UserRolesRequest> request = ArgumentCaptor.forClass(UserRolesRequest.class);
        verify(roleService).replaceUserRoles(eq(accountId), request.capture());
        assertThat(request.getValue().roleIds()).containsExactlyInAnyOrder(heldRole, invitedRole);
    }

    @Test
    @DisplayName("acceptInvitation (employee): links the employee to the account through its setter")
    void acceptEmployeeInvitationLinksEmployee() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);
        UUID employeeId = UUID.randomUUID();
        EmployeeInvitation inv = new EmployeeInvitation(
                tenantId,
                employeeId,
                "emp@example.com",
                hash,
                Instant.now().plus(2, ChronoUnit.DAYS),
                actorUserId,
                "system");
        setId(inv, UUID.randomUUID());
        when(employeeInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        Employee employee = new Employee(tenantId, "test");
        setId(employee, employeeId);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(employee));

        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(anyString(), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, true));
        UUID accountId = UUID.randomUUID();
        UserAccount account = mock(UserAccount.class);
        when(account.getId()).thenReturn(accountId);
        when(userAccountRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(Optional.of(account));
        Role employeeRole = mock(Role.class);
        when(employeeRole.getId()).thenReturn(UUID.randomUUID());
        when(roleRepository.findByTenantIdAndCode(tenantId, "employee")).thenReturn(Optional.of(employeeRole));

        invitationService.acceptInvitation(token);

        assertThat(employee.getUserAccountId()).isEqualTo(accountId);
        verify(employeeRepository).save(employee);
        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("acceptInvitation: expired token is refused, marked EXPIRED and flushed in the same transaction")
    void acceptInvitationExpiredRefused() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId, "user@example.com", hash, Instant.now().minus(1, ChronoUnit.DAYS), actorUserId, "system");
        setId(inv, UUID.randomUUID());

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                .isInstanceOf(InvitationExpiredException.class)
                .hasMessageContaining("expired");

        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(userInvitationRepository).saveAndFlush(inv);
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

    @Test
    @DisplayName(
            "acceptInvitation: triggers compensating deletion of Keycloak user if subsequent DB operation fails and user was newly created")
    void acceptInvitationCompensatesKeycloakUserWhenDbFailsForNewUser() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId, "newuser@example.com", hash, Instant.now().plus(2, ChronoUnit.DAYS), actorUserId, "system");
        setId(inv, UUID.randomUUID());

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(eq("newuser@example.com"), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, true));

        org.mockito.Mockito.doThrow(new RuntimeException("Simulated DB connection failure"))
                .when(userProfileSyncService)
                .sync(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated DB connection failure");

        verify(keycloakProvisioningService).deleteKeycloakUser(keycloakUserId);
    }

    @Test
    @DisplayName(
            "acceptInvitation: does NOT delete Keycloak user if user was pre-existing when subsequent DB operation fails")
    void acceptInvitationDoesNotCompensateKeycloakUserWhenExistingUser() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId, "existing@example.com", hash, Instant.now().plus(2, ChronoUnit.DAYS), actorUserId, "system");
        setId(inv, UUID.randomUUID());

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(eq("existing@example.com"), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, false));

        org.mockito.Mockito.doThrow(new RuntimeException("Simulated DB connection failure"))
                .when(userProfileSyncService)
                .sync(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated DB connection failure");

        verify(keycloakProvisioningService, never()).deleteKeycloakUser(any());
    }
}
