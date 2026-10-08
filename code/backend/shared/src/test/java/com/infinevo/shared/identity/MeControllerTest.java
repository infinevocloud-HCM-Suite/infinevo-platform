package com.infinevo.shared.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.authz.RoleSource;
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
import org.springframework.test.util.ReflectionTestUtils;

/** {@code GET /api/v1/me} for a plain login and while platform staff act inside a tenant (W-65.2). */
class MeControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    private UserProfileSyncService syncService;
    private RoleSource roleSource;
    private MeController controller;

    @BeforeEach
    void setUp() {
        syncService = mock(UserProfileSyncService.class);
        roleSource = mock(RoleSource.class);
        controller = new MeController(syncService, () -> roleSource);
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
        assertThat(view.displayName()).isEqualTo("Me Acme");
    }

    @Test
    @DisplayName("W-73.1: roles are the codes the role source holds for the profile row, sorted")
    void rolesFromTheRoleSource() {
        UUID me = UUID.randomUUID();
        UserAccount account = new UserAccount(TENANT, me, "me@acme.local", "Me", "Acme", Instant.now());
        when(syncService.find(TENANT, me)).thenReturn(Optional.of(account));
        // A UserAccount built here has no id yet (it is assigned on persist), so the stub matches any.
        when(roleSource.roleCodesOf(eq(TENANT), any())).thenReturn(Set.of("payroll-officer", "hr"));
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(me.toString())
                .build();

        assertThat(controller.me(jwt).roles()).containsExactly("hr", "payroll-officer");
    }

    @Test
    @DisplayName("W-73.1: no role source, or one that fails, means no roles - never a 500")
    void rolesFailClosedToEmpty() {
        UUID me = UUID.randomUUID();
        UserAccount account = new UserAccount(TENANT, me, "me@acme.local", null, null, Instant.now());
        when(syncService.find(TENANT, me)).thenReturn(Optional.of(account));
        when(roleSource.roleCodesOf(eq(TENANT), any())).thenThrow(new IllegalStateException("no tenant"));
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(me.toString())
                .build();

        MeController.MeView failing = controller.me(jwt);
        assertThat(failing.roles()).isEmpty();
        assertThat(failing.displayName()).as("no names: the email stands in").isEqualTo("me@acme.local");

        assertThat(new MeController(syncService).me(jwt).roles()).isEmpty();
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

        when(roleSource.roleCodesOf(TENANT, targetAccountId)).thenReturn(Set.of("employee"));

        MeController.MeView view = controller.me(staffToken());

        assertThat(view.userId()).isEqualTo(targetKeycloakId);
        assertThat(view.email()).isEqualTo("emp@globex.local");
        assertThat(view.tenantId()).isEqualTo(TENANT);
        assertThat(view.roles())
                .as("the target user's roles, not the staff member's")
                .containsExactly("employee");
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
        assertThat(view.displayName()).isEqualTo("Staff Infinevo");
        assertThat(view.roles())
                .as("a bootstrap session stands in for the tenant admin")
                .containsExactly("tenant-admin");
    }

    private static Jwt tokenFor(UUID sub) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(sub.toString())
                .build();
    }

    /** A profile row as it comes back from the database: with its id. */
    private static UserAccount savedAccount(UUID keycloakUserId, UUID accountId) {
        UserAccount account = new UserAccount(TENANT, keycloakUserId, "me@acme.local", "Me", "Acme", Instant.now());
        ReflectionTestUtils.setField(account, "id", accountId);
        return account;
    }

    @Test
    @DisplayName("W-73.8: welcomeSeen is what the service holds for the caller's row")
    void welcomeSeenFromTheService() {
        UUID me = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(syncService.find(TENANT, me)).thenReturn(Optional.of(savedAccount(me, accountId)));

        when(syncService.welcomeSeen(TENANT, accountId)).thenReturn(false);
        assertThat(controller.me(tokenFor(me)).welcomeSeen()).isFalse();

        when(syncService.welcomeSeen(TENANT, accountId)).thenReturn(true);
        assertThat(controller.me(tokenFor(me)).welcomeSeen()).isTrue();
    }

    @Test
    @DisplayName("W-73.8: PUT /welcome-seen marks the caller's own row, with the caller as the actor")
    void markWelcomeSeenWritesTheCallersRow() {
        UUID me = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(syncService.find(TENANT, me)).thenReturn(Optional.of(savedAccount(me, accountId)));

        controller.markWelcomeSeen(tokenFor(me));

        verify(syncService).markWelcomeSeen(TENANT, accountId, me.toString());
    }

    @Test
    @DisplayName("W-73.8: staff acting in a tenant never see the welcome page and never dismiss the customer's")
    void actingStaffSkipTheWelcomePage() {
        UUID targetAccountId = UUID.randomUUID();
        when(syncService.findById(TENANT, targetAccountId))
                .thenReturn(Optional.of(savedAccount(UUID.randomUUID(), targetAccountId)));
        ActingAs.set(
                new ActingAs.Impersonation(STAFF, targetAccountId, UUID.randomUUID(), "emp@globex.local", Set.of()));

        assertThat(controller.me(staffToken()).welcomeSeen()).isTrue();
        controller.markWelcomeSeen(staffToken());
        verify(syncService, never()).markWelcomeSeen(any(), any(), anyString());
        verify(syncService, never()).welcomeSeen(any(), any());

        ActingAs.set(new ActingAs.Impersonation(STAFF, null, UUID.randomUUID(), null, Set.of()));
        assertThat(controller.me(staffToken()).welcomeSeen())
                .as("bootstrap session")
                .isTrue();
    }
}
