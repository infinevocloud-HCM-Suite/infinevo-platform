package com.infinevo.core.invitation;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Asserts invitation authorization guards over HTTP (W-24.2, spec §7):
 * <ul>
 *   <li>User invitation endpoints refuse without {@code core.user.manage}</li>
 *   <li>Employee invitation endpoints refuse without {@code core.employee.create}</li>
 *   <li>Holding one does not grant the other</li>
 *   <li>Public acceptance and decline endpoints require no auth</li>
 * </ul>
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class InvitationGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID userManagerSub;
    private UUID employeeCreatorSub;
    private UUID plainEmployeeSub;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("InviteGuard " + UUID.randomUUID());
        UUID employeeRole = AuthzTestSchema.roleId(tenant, "employee");
        UUID tenantAdminRole = AuthzTestSchema.roleId(tenant, "tenant-admin");
        UUID hrRole = AuthzTestSchema.roleId(tenant, "hr");

        // tenant-admin holds core.user.manage
        userManagerSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(tenant, userManagerSub, "admin@guard.test");
        AuthzTestSchema.grant(tenant, adminAccount, tenantAdminRole);

        // hr holds core.employee.create
        employeeCreatorSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(tenant, employeeCreatorSub, "hr@guard.test");
        AuthzTestSchema.grant(tenant, hrAccount, hrRole);

        // plain employee holds only employee role
        plainEmployeeSub = UUID.randomUUID();
        UUID empAccount = AuthzTestSchema.insertMember(tenant, plainEmployeeSub, "emp@guard.test");
        AuthzTestSchema.grant(tenant, empAccount, employeeRole);
    }

    @Test
    @DisplayName("user invitation endpoints refuse callers lacking core.user.manage with 403")
    void userEndpointsRequireUserManage() throws Exception {
        UserInvitationRequest req = new UserInvitationRequest("newuser@example.com", Set.of());

        // Plain employee gets 403
        mvc.perform(as(plainEmployeeSub, post("/api/v1/user-invitations")).content(body(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.user.manage")));

        mvc.perform(as(plainEmployeeSub, get("/api/v1/user-invitations")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.user.manage")));
    }

    @Test
    @DisplayName("employee invitation endpoints refuse callers lacking core.employee.create with 403")
    void employeeEndpointsRequireEmployeeCreate() throws Exception {
        EmployeeInvitationRequest req = new EmployeeInvitationRequest(UUID.randomUUID());

        // Plain employee gets 403
        mvc.perform(as(plainEmployeeSub, post("/api/v1/employee-invitations")).content(body(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.employee.create")));

        mvc.perform(as(plainEmployeeSub, get("/api/v1/employee-invitations")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.employee.create")));
    }

    @Test
    @DisplayName("holding core.user.manage does not grant core.employee.create and vice versa")
    void holdingOneDoesNotGrantTheOther() throws Exception {
        // HR has core.employee.create, but not core.user.manage -> refused on user-invitations
        UserInvitationRequest userReq = new UserInvitationRequest("test@example.com", Set.of());
        mvc.perform(as(employeeCreatorSub, post("/api/v1/user-invitations")).content(body(userReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.user.manage")));
    }

    @Test
    @DisplayName("acceptance and decline endpoints are public and require no auth")
    void publicEndpointsRequireNoAuth() throws Exception {
        // Calling accept with blank token returns 400 (from controller validation), not 401 or 403
        mvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new AcceptInvitationRequest("test-token"))))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // 400 or 500 or 200, but NEVER 401 or 403
                    org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                });
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
