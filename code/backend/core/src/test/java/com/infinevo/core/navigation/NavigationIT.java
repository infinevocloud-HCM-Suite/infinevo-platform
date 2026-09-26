package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration test for the navigation feed (W-12.3, spec section 7).
 *
 * <ul>
 *   <li>Two callers get different feeds from the same endpoint.
 *   <li>{@code actions} equals, exactly, the set the caller's roles grant — read from the schema as
 *       its owner, independently of {@code PermissionService} — and changes on the next call after
 *       the grant is revoked.
 * </ul>
 *
 * <p>Until the HRMS and Payroll endpoints ship, the shipped catalogue holds core items only, so the
 * two feeds differ by role rather than by module. Module filtering is proven in
 * {@code NavigationServiceTest} over a catalogue that has module items.
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

    private final ObjectMapper json = new ObjectMapper();

    private UUID acmeTenant;
    private UUID globexTenant;
    private UUID acmeAdminSub;
    private UUID acmeAdminAccount;
    private UUID acmeAdminRole;
    private UUID globexEmployeeSub;

    @BeforeEach
    void setUp() throws SQLException {
        acmeTenant = AuthzTestSchema.insertTenant("Acme Navigation " + UUID.randomUUID());
        globexTenant = AuthzTestSchema.insertTenant("Globex Navigation " + UUID.randomUUID());
        provisionSubscription(acmeTenant, "ACTIVE", "PAYROLL");
        provisionSubscription(globexTenant, "ACTIVE", "HRMS", "PAYROLL");

        acmeAdminSub = UUID.randomUUID();
        acmeAdminAccount = AuthzTestSchema.insertMember(acmeTenant, acmeAdminSub, "admin@acme.nav.test");
        acmeAdminRole = AuthzTestSchema.roleId(acmeTenant, "tenant-admin");
        AuthzTestSchema.grant(acmeTenant, acmeAdminAccount, acmeAdminRole);

        globexEmployeeSub = UUID.randomUUID();
        UUID globexEmployeeAccount =
                AuthzTestSchema.insertMember(globexTenant, globexEmployeeSub, "employee@globex.nav.test");
        AuthzTestSchema.grant(globexTenant, globexEmployeeAccount, AuthzTestSchema.roleId(globexTenant, "employee"));
    }

    @Test
    @DisplayName("two callers get different feeds from the same endpoint; the admin sees the whole catalogue")
    void twoCallersGetDifferentFeedsFromTheSameEndpoint() throws Exception {
        NavigationResponse admin = feedFor(acmeTenant, acmeAdminSub);
        NavigationResponse employee = feedFor(globexTenant, globexEmployeeSub);

        List<String> catalogueKeys = NavigationCatalogue.DEFAULT_ITEMS.stream()
                .map(ItemDefinition::key)
                .toList();
        assertThat(keysOf(admin)).containsExactlyElementsOf(catalogueKeys);
        assertThat(keysOf(employee)).isNotEqualTo(keysOf(admin));
        assertThat(keysOf(employee)).doesNotContain("core.roles", "core.audit");
    }

    @Test
    @DisplayName("actions equals the caller's granted set exactly, and is empty on the next call after revoke")
    void actionsEqualsGrantedSetExactly_andUpdatesOnRevoke() throws Exception {
        Set<String> granted = AuthzTestSchema.actionsOfRole(acmeAdminRole);
        assertThat(granted).isNotEmpty();

        NavigationResponse before = feedFor(acmeTenant, acmeAdminSub);
        assertThat(before.actions()).containsExactlyInAnyOrderElementsOf(granted);

        revokeRoles(acmeTenant, acmeAdminAccount);
        permissionCache.bumpVersion(acmeTenant);

        NavigationResponse after = feedFor(acmeTenant, acmeAdminSub);
        assertThat(after.actions()).isEmpty();
        assertThat(after.items()).isEmpty();
    }

    private NavigationResponse feedFor(UUID tenantId, UUID sub) throws Exception {
        MvcResult result = mvc.perform(as(tenantId, sub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andReturn();
        return json.readValue(result.getResponse().getContentAsString(), NavigationResponse.class);
    }

    private static List<String> keysOf(NavigationResponse response) {
        return response.items().stream().map(NavigationItemResponse::key).toList();
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
