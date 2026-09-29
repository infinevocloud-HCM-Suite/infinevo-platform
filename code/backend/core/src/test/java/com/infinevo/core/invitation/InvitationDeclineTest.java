package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.authz.RoleService;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Asserts invitation decline rules: stores reason, ends token, rejects blank reasons, rejects expired/revoked (W-24.2, spec §7).
 */
class InvitationDeclineTest {

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

    private InvitationServiceImpl invitationService;
    private final UUID tenantId = UUID.randomUUID();

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

        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("declineInvitation: sets status to DECLINED, stores trimmed reason and timestamp")
    void declineStoresReasonAndEndsToken() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId, "user@example.com", hash, Instant.now().plus(3, ChronoUnit.DAYS), UUID.randomUUID(), "admin");

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        invitationService.declineInvitation(token, " No longer interested in this position ");

        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(inv.getDeclinedAt()).isNotNull();
        assertThat(inv.getDeclineReason()).isEqualTo("No longer interested in this position");

        // Attempting to accept a declined token is refused
        assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declined");
    }

    @Test
    @DisplayName("declineInvitation: blank, empty, or whitespace-only reason is refused")
    void declineBlankReasonRefused() {
        String token = InvitationTokenUtils.generateToken();

        assertThatThrownBy(() -> invitationService.declineInvitation(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> invitationService.declineInvitation(token, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("declineInvitation: reason exceeding 500 characters is refused")
    void declineReasonTooLongRefused() {
        String token = InvitationTokenUtils.generateToken();
        String longReason = "a".repeat(501);

        assertThatThrownBy(() -> invitationService.declineInvitation(token, longReason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 500 characters");
    }

    @Test
    @DisplayName("declineInvitation: declining an expired token is refused")
    void declineExpiredTokenRefused() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId,
                "user@example.com",
                hash,
                Instant.now().minus(1, ChronoUnit.DAYS),
                UUID.randomUUID(),
                "admin");

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> invitationService.declineInvitation(token, "Valid reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("declineInvitation: declining an already accepted or revoked invitation is refused")
    void declineNonPendingRefused() {
        String token = InvitationTokenUtils.generateToken();
        String hash = InvitationTokenUtils.hashToken(token);

        UserInvitation inv = new UserInvitation(
                tenantId, "user@example.com", hash, Instant.now().plus(2, ChronoUnit.DAYS), UUID.randomUUID(), "admin");
        inv.setStatus(InvitationStatus.REVOKED);

        when(userInvitationRepository.findByTokenHashSecurityDefiner(hash)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> invitationService.declineInvitation(token, "Valid reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not in pending status");
    }
}
