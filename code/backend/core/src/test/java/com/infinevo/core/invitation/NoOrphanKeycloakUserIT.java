package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/**
 * Asserts no invitation leaves an orphan Keycloak user (W-24.2, spec §7):
 * <ul>
 *   <li>creating an invitation, and letting it expire, never provisions a Keycloak user;</li>
 *   <li>an acceptance whose database write fails after Keycloak created the user deletes that user again.</li>
 * </ul>
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class NoOrphanKeycloakUserIT extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserInvitationRepository userInvitationRepository;

    @MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    private UUID tenant;
    private UUID adminUserId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("NoOrphan " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an invitation that expires unaccepted never calls Keycloak user provisioning")
    void unacceptedInvitationLeavesNoKeycloakUser() {
        invitationService.createUserInvitation(
                new UserInvitationRequest("unaccepted@example.com", Set.of()), adminUserId);
        verify(keycloakProvisioningService, never()).getOrCreateKeycloakUser(anyString(), any(), any());

        // An expired invitation presented later is refused before Keycloak is touched
        String token = InvitationTokenUtils.generateToken();
        userInvitationRepository.save(new UserInvitation(
                tenant,
                "expired-orphan@example.com",
                InvitationTokenUtils.hashToken(token),
                Instant.now().minus(1, ChronoUnit.HOURS),
                adminUserId,
                "admin"));
        TenantContext.clear();

        assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                .isInstanceOf(InvitationExpiredException.class);
        verify(keycloakProvisioningService, never()).getOrCreateKeycloakUser(anyString(), any(), any());
    }

    @Test
    @DisplayName("a database write failing after Keycloak created the user deletes that Keycloak user")
    void failedWriteAfterProvisioningDeletesTheNewKeycloakUser() throws SQLException {
        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(anyString(), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, true));

        String token = InvitationTokenUtils.generateToken();
        UserInvitation saved = userInvitationRepository.save(new UserInvitation(
                tenant,
                "write-fails@example.com",
                InvitationTokenUtils.hashToken(token),
                Instant.now().plus(7, ChronoUnit.DAYS),
                adminUserId,
                "admin"));
        TenantContext.clear();

        // A real write failure: the core.user_tenant insert for this Keycloak user raises in Postgres
        String trigger = "no_orphan_fail_" + keycloakUserId.toString().replace("-", "");
        try (Connection conn = AuthzTestSchema.migrationConnection();
                Statement st = conn.createStatement()) {
            st.execute("CREATE FUNCTION core." + trigger + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                    + " IF NEW.user_id = '" + keycloakUserId + "' THEN RAISE EXCEPTION 'simulated write failure';"
                    + " END IF; RETURN NEW; END $$");
            st.execute("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON core.user_tenant FOR EACH ROW EXECUTE FUNCTION core." + trigger + "()");
        }
        try {
            assertThatThrownBy(() -> invitationService.acceptInvitation(token))
                    .hasStackTraceContaining("simulated write failure");
        } finally {
            try (Connection conn = AuthzTestSchema.migrationConnection();
                    Statement st = conn.createStatement()) {
                st.execute("DROP TRIGGER " + trigger + " ON core.user_tenant");
                st.execute("DROP FUNCTION core." + trigger + "()");
            }
        }

        verify(keycloakProvisioningService).deleteKeycloakUser(keycloakUserId);
        // The whole acceptance rolled back: still pending, no local account
        assertThat(ownerQuery("SELECT status FROM core.user_invitation WHERE id = ?", saved.getId()))
                .isEqualTo("PENDING");
        assertThat(ownerQuery(
                        "SELECT count(*)::text FROM core.user_account WHERE keycloak_user_id = ?", keycloakUserId))
                .isEqualTo("0");
    }

    private static String ownerQuery(String sql, UUID param) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
