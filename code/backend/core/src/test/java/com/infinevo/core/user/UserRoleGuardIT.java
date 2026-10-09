package com.infinevo.core.user;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.authz.UserRolesRequest;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.invitation.KeycloakProvisioningService;
import com.infinevo.core.invitation.UserInvitationRequest;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The tenant-admin guards (W-73.4 §2, §7): nobody removes their own {@code tenant-admin} or disables their own
 * account, and the last active {@code tenant-admin} can be neither demoted nor disabled — each a {@code 409}.
 * Granting roles through the Users &amp; access invite needs {@code core.role.assign}, and {@code platform-admin}
 * is never granted from inside a tenant, by either path.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class UserRoleGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KeycloakProvisioningService keycloak;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID tenantAdminRole;
    private UUID employeeRole;
    private UUID adminSub;
    private UUID adminAccount;
    private UUID assignerSub;
    private UUID userManagerSub;

    @BeforeEach
    void seed() throws SQLException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = AuthzTestSchema.insertTenant("UserGuard " + suffix);
        tenantAdminRole = AuthzTestSchema.roleId(tenant, "tenant-admin");
        employeeRole = AuthzTestSchema.roleId(tenant, "employee");

        adminSub = UUID.randomUUID();
        adminAccount = AuthzTestSchema.insertMember(tenant, adminSub, "admin-" + suffix + "@guard.test");
        AuthzTestSchema.grant(tenant, adminAccount, tenantAdminRole);

        // Not a tenant-admin, but may assign roles and manage users: the one who could demote the last admin
        assignerSub = UUID.randomUUID();
        UUID assignerAccount = AuthzTestSchema.insertMember(tenant, assignerSub, "assigner-" + suffix + "@guard.test");
        AuthzTestSchema.grant(
                tenant,
                assignerAccount,
                AuthzTestSchema.insertRole(
                        tenant, "assigner", "Assigner", "core.role.assign", "core.user.manage", "core.role.read"));

        // Manages users but may not assign roles
        userManagerSub = UUID.randomUUID();
        UUID managerAccount = AuthzTestSchema.insertMember(tenant, userManagerSub, "um-" + suffix + "@guard.test");
        AuthzTestSchema.grant(
                tenant,
                managerAccount,
                AuthzTestSchema.insertRole(tenant, "user-manager", "Users", "core.user.manage"));
    }

    @Test
    @DisplayName("removing your own tenant-admin is 409, even while another admin exists")
    void ownTenantAdminRemovalRefused() throws Exception {
        UUID second = AuthzTestSchema.insertMember(tenant, UUID.randomUUID(), "second@guard.test");
        AuthzTestSchema.grant(tenant, second, tenantAdminRole);

        mvc.perform(as(adminSub, put("/api/v1/users/" + adminAccount + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("your own tenant-admin")));

        // Another admin may: the guard is about who asks, not about the role
        mvc.perform(as(adminSub, put("/api/v1/users/" + second + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole)))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("removing the last active tenant-admin is 409; with a second active admin it succeeds")
    void lastTenantAdminRemovalRefused() throws Exception {
        mvc.perform(as(assignerSub, put("/api/v1/users/" + adminAccount + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("last active tenant administrator")));

        UUID second = AuthzTestSchema.insertMember(tenant, UUID.randomUUID(), "second@guard.test");
        AuthzTestSchema.grant(tenant, second, tenantAdminRole);
        mvc.perform(as(assignerSub, put("/api/v1/users/" + adminAccount + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole)))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("disabling your own account, or the last active tenant-admin, is 409")
    void disableGuards() throws Exception {
        mvc.perform(as(adminSub, post("/api/v1/users/" + adminAccount + "/disable")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("your own account")));
        mvc.perform(as(assignerSub, post("/api/v1/users/" + adminAccount + "/disable")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("last active tenant administrator")));
    }

    @Test
    @DisplayName("platform-admin is never granted from inside a tenant, by PUT roles or by invitation")
    void platformAdminRefused() throws Exception {
        UUID platformAdmin = AuthzTestSchema.roleId(tenant, "platform-admin");
        UUID target = AuthzTestSchema.insertMember(tenant, UUID.randomUUID(), "target@guard.test");
        mvc.perform(as(adminSub, put("/api/v1/users/" + target + "/roles"))
                        .content(body(new UserRolesRequest(List.of(platformAdmin)))))
                .andExpect(status().isConflict());
        mvc.perform(as(adminSub, post("/api/v1/user-invitations"))
                        .content(body(new UserInvitationRequest("pa@guard.test", Set.of(platformAdmin)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("platform-admin")));
    }

    @Test
    @DisplayName("an invitation carrying roles needs core.role.assign as well as core.user.manage")
    void invitationWithRolesNeedsRoleAssign() throws Exception {
        mvc.perform(as(userManagerSub, post("/api/v1/user-invitations"))
                        .content(body(new UserInvitationRequest("a@guard.test", Set.of(tenantAdminRole)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.role.assign")));
        mvc.perform(as(userManagerSub, put("/api/v1/users/" + adminAccount + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole)))))
                .andExpect(status().isForbidden());
        mvc.perform(as(assignerSub, post("/api/v1/user-invitations"))
                        .content(body(new UserInvitationRequest("b@guard.test", Set.of(employeeRole)))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("disabling or enabling a tenant-admin needs core.role.assign; anyone else needs core.user.manage only")
    void disablingTenantAdminNeedsRoleAssign() throws Exception {
        UUID second = AuthzTestSchema.insertMember(tenant, UUID.randomUUID(), "second-admin@guard.test");
        AuthzTestSchema.grant(tenant, second, tenantAdminRole);
        UUID plain = AuthzTestSchema.insertMember(tenant, UUID.randomUUID(), "plain@guard.test");
        AuthzTestSchema.grant(tenant, plain, employeeRole);

        mvc.perform(as(userManagerSub, post("/api/v1/users/" + second + "/disable")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.role.assign")));
        mvc.perform(as(userManagerSub, post("/api/v1/users/" + second + "/enable")))
                .andExpect(status().isForbidden());
        mvc.perform(as(userManagerSub, post("/api/v1/users/" + plain + "/disable")))
                .andExpect(status().isNoContent());
        mvc.perform(as(assignerSub, post("/api/v1/users/" + second + "/disable")))
                .andExpect(status().isNoContent());
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
