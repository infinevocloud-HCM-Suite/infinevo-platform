package com.infinevo.shared.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.impersonation.ActingAs;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** {@code GET /api/v1/me} for a plain login and while platform staff act inside a tenant (W-65.2). */
class MeControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    private UserProfileSyncService syncService;
    private MeController controller;

    @BeforeEach
    void setUp() {
        syncService = mock(UserProfileSyncService.class);
        controller = new MeController(syncService);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        ActingAs.clear();
        TenantContext.clear();
    }

    private static Jwt staffToken() {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(STAFF.toString())
                .claim("email", "staff@infinevo.local")
                .claim("given_name", "Staff")
                .claim("family_name", "Infinevo")
                .build();
    }

    @Test
    @DisplayName("Not acting: the caller's own profile in the bound tenant")
    void ownProfile() {
        UUID me = UUID.randomUUID();
        UserAccount account = new UserAccount(TENANT, me, "me@acme.local", "Me", "Acme", Instant.now());
        when(syncService.find(TENANT, me)).thenReturn(Optional.of(account));
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(me.toString())
                .build();

        MeController.MeView view = controller.me(jwt);

        assertThat(view.userId()).isEqualTo(me);
        assertThat(view.email()).isEqualTo("me@acme.local");
        assertThat(view.tenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("Acting as a user: the target user's profile, not a 500 for the staff member's missing row")
    void actingAsUser() {
        UUID targetAccountId = UUID.randomUUID();
        UUID targetKeycloakId = UUID.randomUUID();
        UserAccount target =
                new UserAccount(TENANT, targetKeycloakId, "emp@globex.local", "Emp", "Globex", Instant.now());
        when(syncService.findById(TENANT, targetAccountId)).thenReturn(Optional.of(target));
        ActingAs.set(
                new ActingAs.Impersonation(STAFF, targetAccountId, UUID.randomUUID(), "emp@globex.local", Set.of()));

        MeController.MeView view = controller.me(staffToken());

        assertThat(view.userId()).isEqualTo(targetKeycloakId);
        assertThat(view.email()).isEqualTo("emp@globex.local");
        assertThat(view.tenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("Bootstrap session: no customer user exists, so the staff member's own claims in the bound tenant")
    void bootstrapSession() {
        ActingAs.set(new ActingAs.Impersonation(STAFF, null, UUID.randomUUID(), null, Set.of()));

        MeController.MeView view = controller.me(staffToken());

        assertThat(view.userId()).isEqualTo(STAFF);
        assertThat(view.email()).isEqualTo("staff@infinevo.local");
        assertThat(view.firstName()).isEqualTo("Staff");
        assertThat(view.tenantId()).isEqualTo(TENANT);
    }
}
