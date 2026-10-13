package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * An expired token is refused promptly and its row is left {@code EXPIRED} (W-24.2, review B-1).
 *
 * <p>The earlier design locked the row, then marked it expired in a {@code REQUIRES_NEW} transaction on a
 * second connection, which waited on the first connection's lock for ever. Each call here runs under a
 * preemptive timeout, so that hang fails the test instead of freezing the build.
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class InvitationExpiryIT extends AbstractIntegrationTest {

    private static final Duration PROMPTLY = Duration.ofSeconds(20);

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserInvitationRepository userInvitationRepository;

    @Autowired
    private EmployeeInvitationRepository employeeInvitationRepository;

    @MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    private UUID tenant;
    private UUID adminUserId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("Expiry " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("accepting an expired user invitation returns promptly, refused, with the row EXPIRED")
    void expiredUserInvitationAcceptance() throws SQLException {
        String token = InvitationTokenUtils.generateToken();
        UUID id = expiredUserInvitation(token);

        assertTimeoutPreemptively(
                PROMPTLY, () -> assertThatThrownBy(() -> invitationService.acceptInvitation(token, "Str0ng-Passw0rd!"))
                        .isInstanceOf(InvitationExpiredException.class));

        assertThat(status("user_invitation", id)).isEqualTo("EXPIRED");
        verify(keycloakProvisioningService, never()).getOrCreateKeycloakUser(any(), any(), any(), any());
    }

    @Test
    @DisplayName("declining an expired user invitation returns promptly, refused, with the row EXPIRED")
    void expiredUserInvitationDecline() throws SQLException {
        String token = InvitationTokenUtils.generateToken();
        UUID id = expiredUserInvitation(token);

        assertTimeoutPreemptively(
                PROMPTLY, () -> assertThatThrownBy(() -> invitationService.declineInvitation(token, "Too late"))
                        .isInstanceOf(InvitationExpiredException.class));

        assertThat(status("user_invitation", id)).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("accepting an expired employee invitation returns promptly, refused, with the row EXPIRED")
    void expiredEmployeeInvitationAcceptance() throws SQLException {
        UUID employeeId = AuthzTestSchema.insertEmployee(tenant, "EXP-" + UUID.randomUUID(), "Late");
        String token = InvitationTokenUtils.generateToken();
        EmployeeInvitation saved = employeeInvitationRepository.save(new EmployeeInvitation(
                tenant,
                employeeId,
                "late@example.com",
                InvitationTokenUtils.hashToken(token),
                Instant.now().minus(1, ChronoUnit.HOURS),
                adminUserId,
                "admin"));
        TenantContext.clear();

        assertTimeoutPreemptively(
                PROMPTLY, () -> assertThatThrownBy(() -> invitationService.acceptInvitation(token, "Str0ng-Passw0rd!"))
                        .isInstanceOf(InvitationExpiredException.class));

        assertThat(status("employee_invitation", saved.getId())).isEqualTo("EXPIRED");
        verify(keycloakProvisioningService, never()).getOrCreateKeycloakUser(any(), any(), any(), any());
    }

    private UUID expiredUserInvitation(String token) {
        UserInvitation saved = userInvitationRepository.save(new UserInvitation(
                tenant,
                "late-" + UUID.randomUUID() + "@example.com",
                InvitationTokenUtils.hashToken(token),
                Instant.now().minus(1, ChronoUnit.HOURS),
                adminUserId,
                "admin"));
        TenantContext.clear();
        return saved.getId();
    }

    private static String status(String table, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT status FROM core." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
