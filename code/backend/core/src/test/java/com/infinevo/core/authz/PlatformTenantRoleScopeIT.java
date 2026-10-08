package com.infinevo.core.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-33: roles in the Infinevo platform tenant hold platform actions only - {@code core.tenant.*},
 * {@code core.audit.read} and {@code core.user.manage} - so platform staff no longer see customer screens.
 * {@code V158} removed the rest and refuses them from now on; this proves both against real Postgres, and that
 * the staff member's navigation feed follows.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PlatformTenantRoleScopeIT extends AbstractIntegrationTest {

    private static final UUID PLATFORM = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;
    private static final String CHECK_VIOLATION = "23514";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("the platform tenant's platform-admin holds no customer action, only the platform ones")
    void platformAdminHoldsOnlyPlatformActions() throws SQLException {
        Set<String> held = AuthzTestSchema.actionsOfRole(AuthzTestSchema.roleId(PLATFORM, "platform-admin"));

        assertThat(held).doesNotContain("core.employee.read", "core.leave.read", "payroll.run.read");
        assertThat(held).contains("core.tenant.provision", "core.tenant.impersonate", "core.audit.read");
        assertThat(held)
                .allMatch(code -> code.startsWith("core.tenant.")
                        || code.equals("core.audit.read")
                        || code.equals("core.user.manage"));
    }

    @Test
    @DisplayName("no role in the platform tenant holds an action outside the platform's scope")
    void noPlatformRoleHoldsACustomerAction() throws SQLException {
        for (String role : AuthzTestSchema.SYSTEM_ROLES) {
            assertThat(AuthzTestSchema.actionsOfRole(AuthzTestSchema.roleId(PLATFORM, role)))
                    .as("platform tenant's %s", role)
                    .allMatch(code -> code.startsWith("core.tenant.")
                            || code.equals("core.audit.read")
                            || code.equals("core.user.manage"));
        }
    }

    @Test
    @DisplayName("granting core.employee.read to the platform tenant's platform-admin is refused (23514)")
    void customerGrantInThePlatformTenantIsRefused() throws SQLException {
        UUID platformAdmin = AuthzTestSchema.roleId(PLATFORM, "platform-admin");

        SQLException refused = catchThrowableOfType(
                () -> grantAsOwner(PLATFORM, platformAdmin, "core.employee.read"), SQLException.class);

        assertThat((Throwable) refused).as("the insert is refused").isNotNull();
        assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
        assertThat(AuthzTestSchema.actionsOfRole(platformAdmin)).doesNotContain("core.employee.read");
    }

    @Test
    @DisplayName("a custom role in the platform tenant cannot hold a customer action either; platform ones it can")
    void customPlatformRoleIsHeldToTheSameScope() throws SQLException {
        UUID custom = AuthzTestSchema.insertRole(PLATFORM, "support-" + UUID.randomUUID(), "Support");

        SQLException refused =
                catchThrowableOfType(() -> grantAsOwner(PLATFORM, custom, "core.org.read"), SQLException.class);
        assertThat((Throwable) refused).isNotNull();
        assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);

        grantAsOwner(PLATFORM, custom, "core.audit.read");
        assertThat(AuthzTestSchema.actionsOfRole(custom)).containsExactly("core.audit.read");
    }

    @Test
    @DisplayName(
            "a customer tenant's roles are untouched: its tenant-admin still reads employees, a custom role may too")
    void customerTenantIsUntouched() throws SQLException {
        UUID customer = AuthzTestSchema.insertTenant("Scope Customer " + UUID.randomUUID());

        assertThat(AuthzTestSchema.actionsOfRole(AuthzTestSchema.roleId(customer, "tenant-admin")))
                .contains("core.employee.read", "core.leave.read");
        UUID custom = AuthzTestSchema.insertRole(customer, "reader-" + UUID.randomUUID(), "Reader");
        grantAsOwner(customer, custom, "core.employee.read");
        assertThat(AuthzTestSchema.actionsOfRole(custom)).containsExactly("core.employee.read");
    }

    @Test
    @DisplayName("platform staff's feed has Dashboard, Tenants and no customer screen, and lands on /admin (W-73.2)")
    void platformStaffFeedHasNoCustomerScreen() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID account = AuthzTestSchema.insertMember(PLATFORM, sub, "staff-" + sub + "@infinevo.test");
        AuthzTestSchema.grant(PLATFORM, account, AuthzTestSchema.roleId(PLATFORM, "platform-admin"));

        String body = mvc.perform(get("/api/v1/navigation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(jwt().jwt(token ->
                                token.subject(sub.toString()).claim("tenant_id", PLATFORM.toString()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode feed = json.readTree(body);
        List<String> keys = new ArrayList<>();
        collectKeys(feed.path("items"), keys);

        assertThat(keys).contains("core.admin.home", "core.tenants", "core.audit");
        assertThat(keys)
                .doesNotContain(
                        "core.employee",
                        "core.org",
                        "core.roles",
                        "core.holiday",
                        "core.leave",
                        "core.approvals",
                        "core.invitations.employees",
                        "core.setup",
                        "core.invitations.users",
                        "core.people");
        assertThat(keys)
                .as("Settings holds Audit only")
                .containsExactly("core.admin.home", "core.settings", "core.audit", "core.tenants");
        assertThat(feed.path("homePath").asText()).isEqualTo("/admin");
    }

    private static void collectKeys(JsonNode items, List<String> into) {
        items.forEach(item -> {
            into.add(item.path("key").asText());
            collectKeys(item.path("children"), into);
        });
    }

    /** As the schema owner, which bypasses row-level security but not the V158 trigger. */
    private static void grantAsOwner(UUID tenant, UUID role, String action) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenant);
            ps.setObject(2, role);
            ps.setString(3, action);
            ps.executeUpdate();
        }
    }
}
