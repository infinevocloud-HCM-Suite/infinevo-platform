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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * W-73.3 over HTTP: {@code GET /api/v1/employees/{id}/access} and the role rule on
 * {@code POST /api/v1/employee-invitations} — an invitation that carries roles needs {@code core.role.assign}
 * as well as {@code core.employee.create}, the same action that guards {@code PUT /users/{id}/roles}.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class EmployeeInvitationHttpIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID hrRole;
    private UUID nobodySub;
    private UUID readerSub;
    private UUID inviterSub;
    private UUID assignerSub;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("InviteHttp " + UUID.randomUUID());
        hrRole = AuthzTestSchema.roleId(tenant, "hr");
        nobodySub = member("nobody", AuthzTestSchema.insertRole(tenant, "no-actions", "No actions"));
        readerSub =
                member("reader", AuthzTestSchema.insertRole(tenant, "employee-reader", "Reader", "core.employee.read"));
        inviterSub =
                member("inviter", AuthzTestSchema.insertRole(tenant, "inviter", "Inviter", "core.employee.create"));
        assignerSub = member(
                "assigner",
                AuthzTestSchema.insertRole(
                        tenant,
                        "inviter-assigner",
                        "Inviter and assigner",
                        "core.employee.create",
                        "core.role.assign"));
    }

    @Test
    @DisplayName("GET access: 403 without core.employee.read, 200 with it, 404 for another tenant's employee")
    void accessGuardAndTenantScope() throws Exception {
        UUID employee = insertEmployee(tenant);
        UUID otherTenant = AuthzTestSchema.insertTenant("InviteHttp other " + UUID.randomUUID());
        UUID otherEmployee = insertEmployee(otherTenant);

        mvc.perform(as(nobodySub, get("/api/v1/employees/{id}/access", employee)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.employee.read")));

        mvc.perform(as(readerSub, get("/api/v1/employees/{id}/access", employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NONE"));

        mvc.perform(as(readerSub, get("/api/v1/employees/{id}/access", otherEmployee)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("POST without core.employee.create is 403")
    void inviteNeedsEmployeeCreate() throws Exception {
        mvc.perform(as(nobodySub, post("/api/v1/employee-invitations"))
                        .content(body(new EmployeeInvitationRequest(insertEmployee(tenant), Set.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.employee.create")));
    }

    @Test
    @DisplayName("POST with roleIds and without core.role.assign is 403, and nothing is written")
    void rolesNeedRoleAssign() throws Exception {
        UUID employee = insertEmployee(tenant);

        mvc.perform(as(inviterSub, post("/api/v1/employee-invitations"))
                        .content(body(new EmployeeInvitationRequest(employee, Set.of(hrRole)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.role.assign")));

        mvc.perform(as(inviterSub, get("/api/v1/employee-invitations").param("employeeId", employee.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST with roleIds and core.role.assign is 201")
    void rolesWithRoleAssignCreated() throws Exception {
        mvc.perform(as(assignerSub, post("/api/v1/employee-invitations"))
                        .content(body(new EmployeeInvitationRequest(insertEmployee(tenant), Set.of(hrRole)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleIds[0]").value(hrRole.toString()));
    }

    @Test
    @DisplayName("POST with empty or absent roleIds needs only core.employee.create: 201")
    void noRolesNeedsOnlyEmployeeCreate() throws Exception {
        mvc.perform(as(inviterSub, post("/api/v1/employee-invitations"))
                        .content(body(new EmployeeInvitationRequest(insertEmployee(tenant), Set.of()))))
                .andExpect(status().isCreated());

        mvc.perform(as(inviterSub, post("/api/v1/employee-invitations"))
                        .content("{\"employeeId\":\"" + insertEmployee(tenant) + "\"}"))
                .andExpect(status().isCreated());
    }

    private UUID member(String name, UUID roleId) throws SQLException {
        UUID sub = UUID.randomUUID();
        UUID account = AuthzTestSchema.insertMember(tenant, sub, name + "-" + sub + "@http.test");
        AuthzTestSchema.grant(tenant, account, roleId);
        return sub;
    }

    /** An employee with a work email, which an invitation needs; as the schema owner. */
    private static UUID insertEmployee(UUID tenantId) throws SQLException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (tenant_id, employee_number, first_name, work_email, date_of_joining, status,
                             created_by, updated_by)
                        VALUES (?, ?, 'Jane', ?, '2026-01-01', 'ACTIVE', 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, "EMP-" + suffix);
            ps.setString(3, "jane." + suffix + "@http.test");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
