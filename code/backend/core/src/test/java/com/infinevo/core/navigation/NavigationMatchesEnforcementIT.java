package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-12.3 §7: For every item in the feed, the target endpoint returns non-403;
 * for every item absent, it returns 403.
 *
 * <p>Catches the menu and the enforcement drifting apart in either direction —
 * a visible item that refuses, or a hidden item that works.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class NavigationMatchesEnforcementIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID acmeTenant;
    private UUID acmeAdminSub;

    private UUID globexTenant;
    private UUID globexEmployeeSub;

    @BeforeEach
    void setUp() throws SQLException {
        // Acme has PAYROLL only
        acmeTenant = AuthzTestSchema.insertTenant("Acme Match " + UUID.randomUUID());
        provisionSubscription(acmeTenant, "ACTIVE", "PAYROLL");
        acmeAdminSub = UUID.randomUUID();
        UUID acmeAdminAccount = AuthzTestSchema.insertMember(acmeTenant, acmeAdminSub, "admin@acme.match.test");
        AuthzTestSchema.grant(acmeTenant, acmeAdminAccount, AuthzTestSchema.roleId(acmeTenant, "tenant-admin"));

        // Globex has HRMS + PAYROLL, but user has employee role only
        globexTenant = AuthzTestSchema.insertTenant("Globex Match " + UUID.randomUUID());
        provisionSubscription(globexTenant, "ACTIVE", "HRMS", "PAYROLL");
        globexEmployeeSub = UUID.randomUUID();
        UUID globexEmployeeAccount =
                AuthzTestSchema.insertMember(globexTenant, globexEmployeeSub, "employee@globex.match.test");
        AuthzTestSchema.grant(globexTenant, globexEmployeeAccount, AuthzTestSchema.roleId(globexTenant, "employee"));
    }

    @Test
    @DisplayName("Acme admin: present items return non-403, absent items (HRMS) return 403")
    void acmeAdmin_presentItemsReturnNon403_absentReturn403() throws Exception {
        MvcResult navResult = mvc.perform(as(acmeTenant, acmeAdminSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andReturn();

        NavigationResponse nav = json.readValue(navResult.getResponse().getContentAsString(), NavigationResponse.class);
        Set<String> visibleKeys = collectAllKeys(nav.items());

        // Catalogue items
        for (NavigationCatalogue.ItemDefinition def : NavigationCatalogue.DEFAULT_ITEMS) {
            verifyItemEnforcement(acmeTenant, acmeAdminSub, def, visibleKeys);
        }
    }

    @Test
    @DisplayName("Globex employee: unheld admin items are absent and return 403 FORBIDDEN")
    void globexEmployee_absentAdminItemsReturn403Forbidden() throws Exception {
        MvcResult navResult = mvc.perform(as(globexTenant, globexEmployeeSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andReturn();

        NavigationResponse nav = json.readValue(navResult.getResponse().getContentAsString(), NavigationResponse.class);
        Set<String> visibleKeys = collectAllKeys(nav.items());

        // Catalogue items
        for (NavigationCatalogue.ItemDefinition def : NavigationCatalogue.DEFAULT_ITEMS) {
            verifyItemEnforcement(globexTenant, globexEmployeeSub, def, visibleKeys);
        }
    }

    private void verifyItemEnforcement(
            UUID tenantId, UUID sub, NavigationCatalogue.ItemDefinition def, Set<String> visibleKeys) throws Exception {
        if (def.hasChildren()) {
            for (NavigationCatalogue.ItemDefinition child : def.children()) {
                verifyItemEnforcement(tenantId, sub, child, visibleKeys);
            }
            return;
        }

        boolean isVisible = visibleKeys.contains(def.key());
        MvcResult endpointResult =
                mvc.perform(as(tenantId, sub, get(def.targetEndpoint()))).andReturn();

        int httpStatus = endpointResult.getResponse().getStatus();
        if (isVisible) {
            assertThat(httpStatus)
                    .as("Visible menu item '%s' must not return 403 on endpoint '%s'", def.key(), def.targetEndpoint())
                    .isNotEqualTo(HttpStatus.FORBIDDEN.value());
        } else {
            assertThat(httpStatus)
                    .as("Absent menu item '%s' must return 403 on endpoint '%s'", def.key(), def.targetEndpoint())
                    .isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    private Set<String> collectAllKeys(List<NavigationItemResponse> items) {
        Set<String> keys = new HashSet<>();
        for (NavigationItemResponse item : items) {
            keys.add(item.key());
            if (item.children() != null && !item.children().isEmpty()) {
                keys.addAll(collectAllKeys(item.children()));
            }
        }
        return keys;
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

    private MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
