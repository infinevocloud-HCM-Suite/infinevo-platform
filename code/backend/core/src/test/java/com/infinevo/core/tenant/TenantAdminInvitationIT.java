package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * D-42: creating a tenant with an administrator email invites that address into the new tenant as its
 * {@code tenant-admin}, and the platform's tenant list reports the invitation's state.
 *
 * <p>Runs as {@code app_user} against real Postgres, bound to the platform tenant as a platform admin's
 * request is, so row-level security on {@code core.user_invitation}, {@code core.user_invitation_role} and
 * {@code core.notification} is what the provisioning has to satisfy. Rows are then read as the schema
 * owner, past RLS.
 */
@SpringBootTest(
        classes = TenantAdminInvitationIT.App.class,
        properties = "invitation.link.base-url=" + TenantAdminInvitationIT.LINK_BASE)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class TenantAdminInvitationIT extends AbstractIntegrationTest {

    static final String LINK_BASE = "https://app.infinevo.test/invitations/accept";

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantQueryService tenantQueryService;

    @Autowired
    private PlatformTenant platformTenant;

    private UUID actorUserId;

    /** The authz test database has no notification tables; add the shipped scripts once. */
    @BeforeAll
    static void notificationTables() throws Exception {
        try (Connection conn = AuthzTestSchema.migrationConnection()) {
            if (!exists(conn, "core.notification")) {
                for (String script : new String[] {
                    "core/V038__notification_template.sql",
                    "core/V039__notification.sql",
                    "core/V093__reminder_rule.sql",
                    "core/V096__scheduled_report_notification.sql"
                }) {
                    try (InputStream is = TenantAdminInvitationIT.class
                                    .getClassLoader()
                                    .getResourceAsStream("db/migration/" + script);
                            Statement st = conn.createStatement()) {
                        st.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    @BeforeEach
    void bindPlatformTenant() {
        actorUserId = UUID.randomUUID();
        TenantContext.set(platformTenant.tenantId());
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("with admin_email: a tenant-admin invitation is written in the new tenant and its email is queued")
    void provisionWithAdminEmail_invitesTheAdministratorInTheNewTenant() throws SQLException {
        String email = "Admin-" + UUID.randomUUID() + "@Example.test";
        String expectedEmail = email.toLowerCase();

        TenantResponse response =
                tenantService.provisionTenant(request("Admin Corp " + UUID.randomUUID(), email), actorUserId);

        UUID tenantId = response.tenantId();
        UUID invitationId = response.adminInvitationId();
        assertThat(invitationId).as("the response names the invitation").isNotNull();
        assertThat(TenantContext.current())
                .as("the platform binding is restored")
                .contains(platformTenant.tenantId());

        // The invitation row belongs to the new tenant, not the platform tenant that made it.
        List<String> row = ownerRow(
                "SELECT tenant_id::text, email, status, invited_by_user_id::text FROM core.user_invitation WHERE id = ?",
                invitationId);
        assertThat(row).containsExactly(tenantId.toString(), expectedEmail, "PENDING", actorUserId.toString());

        // It carries exactly the new tenant's tenant-admin role.
        assertThat(ownerStrings(
                        """
                        SELECT r.code || '/' || uir.tenant_id::text
                        FROM core.user_invitation_role uir
                        JOIN core.role r ON r.tenant_id = uir.tenant_id AND r.id = uir.role_id
                        WHERE uir.invitation_id = ?
                        """,
                        invitationId))
                .containsExactly("tenant-admin/" + tenantId);

        // The email is queued in the new tenant, to the administrator, with the accept link.
        List<String> emails = ownerStrings(
                "SELECT recipient_email || '|' || status || '|' || body FROM core.notification"
                        + " WHERE tenant_id = ? AND event = 'USER_INVITATION' AND channel = 'EMAIL'",
                tenantId);
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0)).startsWith(expectedEmail + "|QUEUED|").contains(LINK_BASE + "?token=");

        // Nothing was written under the platform tenant.
        assertThat(ownerStrings(
                        "SELECT id::text FROM core.user_invitation WHERE tenant_id = ? AND email = '" + expectedEmail
                                + "'",
                        platformTenant.tenantId()))
                .isEmpty();

        // The platform's tenant list reports it, across RLS, through core.list_tenants() (V159).
        TenantOverview.AdminInvitation listed = listed(tenantId).adminInvitation();
        assertThat(listed.status()).isEqualTo(TenantOverview.AdminInvitationStatus.PENDING);
        assertThat(listed.email()).isEqualTo(expectedEmail);
        assertThat(tenantQueryService.getOverview(tenantId).adminInvitation()).isEqualTo(listed);

        // Once accepted, the list says so.
        ownerUpdate(
                "UPDATE core.user_invitation SET status = 'ACCEPTED', accepted_at = now() WHERE id = ?", invitationId);
        TenantOverview.AdminInvitation accepted = listed(tenantId).adminInvitation();
        assertThat(accepted.status()).isEqualTo(TenantOverview.AdminInvitationStatus.ACCEPTED);
        assertThat(accepted.email()).isEqualTo(expectedEmail);
    }

    @Test
    @DisplayName("without admin_email: no invitation, no email, and the list reports NONE")
    void provisionWithoutAdminEmail_invitesNobody() throws SQLException {
        TenantResponse response =
                tenantService.provisionTenant(request("Quiet Corp " + UUID.randomUUID(), null), actorUserId);

        UUID tenantId = response.tenantId();
        assertThat(response.adminInvitationId()).isNull();
        assertThat(ownerStrings("SELECT id::text FROM core.user_invitation WHERE tenant_id = ?", tenantId))
                .isEmpty();
        assertThat(ownerStrings(
                        "SELECT id::text FROM core.notification WHERE tenant_id = ? AND event = 'USER_INVITATION'",
                        tenantId))
                .isEmpty();

        TenantOverview.AdminInvitation listed = listed(tenantId).adminInvitation();
        assertThat(listed.status()).isEqualTo(TenantOverview.AdminInvitationStatus.NONE);
        assertThat(listed.email()).isNull();
    }

    @Test
    @DisplayName("a malformed admin_email is refused before anything is provisioned")
    void malformedAdminEmail_provisionsNothing() throws SQLException {
        String name = "Bad Email Corp " + UUID.randomUUID();

        assertThatThrownBy(() -> tenantService.provisionTenant(request(name, "not-an-email"), actorUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admin_email");

        assertThat(tenantsNamed(name)).isEmpty();
    }

    @Test
    @DisplayName("an admin_email with no authenticated inviter is refused before anything is provisioned")
    void adminEmailWithoutActor_provisionsNothing() throws SQLException {
        String name = "No Actor Corp " + UUID.randomUUID();

        assertThatThrownBy(() -> tenantService.provisionTenant(request(name, "admin@example.test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inviter");

        assertThat(tenantsNamed(name)).isEmpty();
    }

    private static TenantRequest request(String name, String adminEmail) {
        return new TenantRequest(name, "IN", "Asia/Kolkata", (short) 4, Set.of(), adminEmail);
    }

    private TenantOverview listed(UUID tenantId) {
        return tenantQueryService.list().stream()
                .filter(t -> t.tenantId().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tenant " + tenantId + " not in core.list_tenants()"));
    }

    private static List<String> tenantsNamed(String name) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT tenant_id::text FROM core.tenant WHERE name = ?")) {
            ps.setString(1, name);
            return strings(ps);
        }
    }

    private static List<String> ownerRow(String sql, UUID param) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("row for " + param).isTrue();
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    values.add(rs.getString(i));
                }
                return values;
            }
        }
    }

    private static List<String> ownerStrings(String sql, UUID param) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            return strings(ps);
        }
    }

    private static List<String> strings(PreparedStatement ps) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    private static void ownerUpdate(String sql, UUID param) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            ps.executeUpdate();
        }
    }

    private static boolean exists(Connection conn, String relation) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, relation);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    /** The invitation test context plus the notification composer, as {@code InvitationTokenPersistenceIT}. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.shared.authz"
            },
            excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
            })
    @EntityScan(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.shared.identity"
            })
    @EnableJpaRepositories(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.shared.identity"
            })
    @Import(UserProfileSyncService.class)
    static class App {}
}
