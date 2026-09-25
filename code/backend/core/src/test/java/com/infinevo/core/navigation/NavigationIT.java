package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration test for the navigation feed (W-12.3, spec section 7).
 *
 * <p>Verifies:
 * <ul>
 *   <li>Acme and Globex get different feeds from the same endpoint.
 *   <li>Actions equals the caller's {@link PermissionService} set exactly.
 *   <li>Actions change on the next call after a role grant is revoked.
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
class NavigationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PermissionCache permissionCache;

    @Autowired
    private PermissionService permissionService;

    private final ObjectMapper json = new ObjectMapper();

    private UUID acmeTenant;
    private UUID globexTenant;
    private UUID acmeAdminSub;
    private UUID globexAdminSub;
    private UUID acmeAdminAccount;

    @BeforeEach
    void setUp() throws SQLException {
        acmeTenant = AuthzTestSchema.insertTenant("Acme Navigation " + UUID.randomUUID());
        globexTenant = AuthzTestSchema.insertTenant("Globex Navigation " + UUID.randomUUID());

        // Acme has PAYROLL only; Globex has HRMS + PAYROLL
        provisionSubscription(acmeTenant, "ACTIVE", "PAYROLL");
        provisionSubscription(globexTenant, "ACTIVE", "HRMS", "PAYROLL");

        acmeAdminSub = UUID.randomUUID();
        acmeAdminAccount = AuthzTestSchema.insertMember(acmeTenant, acmeAdminSub, "admin@acme.nav.test");
        AuthzTestSchema.grant(acmeTenant, acmeAdminAccount, AuthzTestSchema.roleId(acmeTenant, "tenant-admin"));

        globexAdminSub = UUID.randomUUID();
        UUID globexAdminAccount = AuthzTestSchema.insertMember(globexTenant, globexAdminSub, "admin@globex.nav.test");
        AuthzTestSchema.grant(globexTenant, globexAdminAccount, AuthzTestSchema.roleId(globexTenant, "tenant-admin"));
    }

    @Test
    @DisplayName("Acme and Globex get different feeds from the same endpoint")
    void acmeAndGlobexGetDifferentFeedsFromSameEndpoint() throws Exception {
        // Acme (Payroll only): sees core and payroll, but NO hrms.*
        mvc.perform(as(acmeTenant, acmeAdminSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].key", hasItem("core.employee")))
                .andExpect(jsonPath("$.items[*].key", hasItem("payroll.runs")))
                .andExpect(jsonPath("$.items[*].key", not(hasItem("hrms.timesheets"))));

        // Globex (HRMS + Payroll): sees both hrms.* and payroll.*
        mvc.perform(as(globexTenant, globexAdminSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].key", hasItem("core.employee")))
                .andExpect(jsonPath("$.items[*].key", hasItem("hrms.timesheets")))
                .andExpect(jsonPath("$.items[*].key", hasItem("payroll.runs")));
    }

    @Test
    @DisplayName("actions equals the caller's PermissionService set exactly, and changes on next call after revoke")
    void actionsEqualsCallerPermissionServiceSetExactly_andUpdatesOnRevoke() throws Exception {
        MvcResult result = mvc.perform(as(acmeTenant, acmeAdminSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andReturn();

        NavigationResponse response =
                json.readValue(result.getResponse().getContentAsString(), NavigationResponse.class);

        assertThat(response.actions()).isNotEmpty();
        assertThat(response.actions()).contains("core.employee.read", "payroll.run.read");

        // Revoke the role from Acme admin and bump version
        revokeRoles(acmeTenant, acmeAdminAccount);
        permissionCache.bumpVersion(acmeTenant);

        // Next call immediately reflects the change: empty actions and no items requiring actions
        MvcResult updatedResult = mvc.perform(as(acmeTenant, acmeAdminSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andReturn();

        NavigationResponse updatedResponse =
                json.readValue(updatedResult.getResponse().getContentAsString(), NavigationResponse.class);

        assertThat(updatedResponse.actions()).isEmpty();
        assertThat(updatedResponse.items()).isEmpty();
    }

    private void provisionSubscription(UUID tenantId, String status, String... modules) throws SQLException {
        UUID subId = UUID.randomUUID();
        try (Connection conn = AuthzTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription (id, tenant_id, status, started_on) VALUES (?, ?, ?, CURRENT_DATE)")) {
                ps.setObject(1, subId);
                ps.setObject(2, tenantId);
                ps.setString(3, status);
                ps.executeUpdate();
            }
            for (String mod : modules) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on) VALUES (?, ?, ?, ?, CURRENT_DATE)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, subId);
                    ps.setString(4, mod);
                    ps.executeUpdate();
                }
            }
        }
    }

    private void revokeRoles(UUID tenantId, UUID accountId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM core.user_role WHERE tenant_id = ? AND user_account_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, accountId);
            ps.executeUpdate();
        }
    }

    private MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
