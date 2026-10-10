package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * W-12.3 §7 — the ticket's reason to exist: for every leaf of the shipped catalogue, a visible item's
 * endpoint answers 2xx and an absent item's endpoint answers 403, <em>against the real controllers</em>.
 *
 * <p>There are no stand-ins. {@link PermissionGuardTestApp} holds the org, role and audit controllers
 * the catalogue targets, and {@code NavigationCatalogueValidator} refuses to start the context if a
 * catalogue leaf has no mapping — so a catalogue entry for an endpoint that does not exist fails here
 * before a single request is made.
 *
 * <p>The visible branch asserts 2xx, not merely "not 403": a visible item that answers 404 or 405 is
 * as broken as one that refuses.
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

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private final ObjectMapper json = new ObjectMapper();

    private UUID acmeTenant;
    private UUID acmeAdminSub;

    private UUID globexTenant;
    private UUID globexEmployeeSub;
    private UUID globexHrSub;

    @BeforeEach
    void setUp() throws SQLException {
        acmeTenant = AuthzTestSchema.insertTenant("Acme Match " + UUID.randomUUID());
        provisionSubscription(acmeTenant, "ACTIVE", "PAYROLL");
        acmeAdminSub = UUID.randomUUID();
        UUID acmeAdminAccount = AuthzTestSchema.insertMember(acmeTenant, acmeAdminSub, "admin@acme.match.test");
        AuthzTestSchema.grant(acmeTenant, acmeAdminAccount, AuthzTestSchema.roleId(acmeTenant, "tenant-admin"));
        // D-75: the self-service item shows only for a login linked to an employee record, as real users are.
        AuthzTestSchema.linkEmployee(
                AuthzTestSchema.insertEmployee(acmeTenant, "MATCH-ADMIN", "Admin"), acmeAdminAccount);

        globexTenant = AuthzTestSchema.insertTenant("Globex Match " + UUID.randomUUID());
        provisionSubscription(globexTenant, "ACTIVE", "HRMS", "PAYROLL");
        globexEmployeeSub = UUID.randomUUID();
        UUID globexEmployeeAccount =
                AuthzTestSchema.insertMember(globexTenant, globexEmployeeSub, "employee@globex.match.test");
        AuthzTestSchema.grant(globexTenant, globexEmployeeAccount, AuthzTestSchema.roleId(globexTenant, "employee"));
        AuthzTestSchema.linkEmployee(
                AuthzTestSchema.insertEmployee(globexTenant, "MATCH-EMP", "Employee"), globexEmployeeAccount);
        globexHrSub = UUID.randomUUID();
        UUID globexHrAccount = AuthzTestSchema.insertMember(globexTenant, globexHrSub, "hr@globex.match.test");
        AuthzTestSchema.grant(globexTenant, globexHrAccount, AuthzTestSchema.roleId(globexTenant, "hr"));
        AuthzTestSchema.linkEmployee(AuthzTestSchema.insertEmployee(globexTenant, "MATCH-HR", "Hr"), globexHrAccount);
    }

    @Test
    @DisplayName("tenant admin: customer leaves are visible and answer 2xx; platform leaves absent and answer 403")
    void tenantAdmin_everyLeafVisibleAndEveryTargetAnswers2xx() throws Exception {
        Set<String> visible = visibleKeys(acmeTenant, acmeAdminSub);

        List<String> customerLeaves = leafKeys(NavigationCatalogue.DEFAULT_ITEMS).stream()
                .filter(k -> !k.equals("core.tenants") && !k.equals("core.admin.home"))
                .toList();
        assertThat(visible).containsAll(customerLeaves);
        assertThat(visible).doesNotContain("core.tenants", "core.admin.home");
        walk(acmeTenant, acmeAdminSub, visible);
    }

    @Test
    @DisplayName("employee: the admin-only leaves are absent and answer 403; the rest agree both ways")
    void employee_adminLeavesAbsentAndAnswer403() throws Exception {
        Set<String> visible = visibleKeys(globexTenant, globexEmployeeSub);

        // The seeded employee role reads the org masters but neither roles nor the audit trail,
        // so this caller exercises both branches of the walk.
        assertThat(visible).doesNotContain("core.roles", "core.audit", "core.tenants", "core.admin.home");
        assertThat(visible).isNotEmpty();
        walk(globexTenant, globexEmployeeSub, visible);
    }

    @Test
    @DisplayName("hr: whatever the feed shows agrees with the endpoints, in both directions")
    void hr_feedAndEnforcementAgreeBothWays() throws Exception {
        walk(globexTenant, globexHrSub, visibleKeys(globexTenant, globexHrSub));
    }

    private Set<String> visibleKeys(UUID tenantId, UUID sub) throws Exception {
        MvcResult navResult = mvc.perform(as(tenantId, sub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andReturn();
        NavigationResponse nav = json.readValue(navResult.getResponse().getContentAsString(), NavigationResponse.class);
        return collectAllKeys(nav.items());
    }

    private void walk(UUID tenantId, UUID sub, Set<String> visibleKeys) throws Exception {
        for (ItemDefinition def : NavigationCatalogue.DEFAULT_ITEMS) {
            verifyItemEnforcement(tenantId, sub, def, visibleKeys);
        }
    }

    private void verifyItemEnforcement(UUID tenantId, UUID sub, ItemDefinition def, Set<String> visibleKeys)
            throws Exception {
        if (def.hasChildren()) {
            for (ItemDefinition child : def.children()) {
                verifyItemEnforcement(tenantId, sub, child, visibleKeys);
            }
            return;
        }

        int httpStatus = mvc.perform(as(tenantId, sub, get(def.targetEndpoint())))
                .andReturn()
                .getResponse()
                .getStatus();

        if (visibleKeys.contains(def.key())) {
            assertThat(httpStatus)
                    .as("Visible menu item '%s' must answer 2xx on '%s'", def.key(), def.targetEndpoint())
                    .isBetween(200, 299);
        } else {
            // Hidden because the caller holds none of its actions - that is the menu's rule. The endpoint
            // usually refuses too. It may answer only when its own @RequiresAction.anyOf names an action the
            // caller holds: GET /leave-requests lists HR's register under core.leave.read and, under
            // core.leave.read_own, the caller's own rows. Anything else answering 2xx is an enforcement gap.
            Set<String> held = heldActions(tenantId, sub);
            assertThat(held)
                    .as("Absent menu item '%s': the caller must hold none of its actions", def.key())
                    .doesNotContainAnyElementsOf(def.actions());
            if (httpStatus != HttpStatus.FORBIDDEN.value()) {
                Set<String> anyOf = endpointAnyOf(def.targetEndpoint());
                assertThat(anyOf)
                        .as(
                                "Absent menu item '%s' answered %d on '%s', whose anyOf %s names nothing the caller holds",
                                def.key(), httpStatus, def.targetEndpoint(), anyOf)
                        .anyMatch(held::contains);
                assertThat(httpStatus).isBetween(200, 299);
            }
        }
    }

    /** The {@code anyOf} codes of the {@code @RequiresAction} guarding {@code GET path}; empty when it has none. */
    private Set<String> endpointAnyOf(String path) throws Exception {
        HandlerExecutionChain chain = requestMappingHandlerMapping.getHandler(new MockHttpServletRequest("GET", path));
        assertThat(chain).as("GET %s has a handler", path).isNotNull();
        HandlerMethod handler = (HandlerMethod) chain.getHandler();
        RequiresAction guard = handler.getMethodAnnotation(RequiresAction.class);
        if (guard == null) {
            guard = handler.getBeanType().getAnnotation(RequiresAction.class);
        }
        return guard == null ? Set.of() : Set.of(guard.anyOf());
    }

    private Set<String> heldActions(UUID tenantId, UUID sub) throws Exception {
        MvcResult navResult = mvc.perform(as(tenantId, sub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andReturn();
        return json.readValue(navResult.getResponse().getContentAsString(), NavigationResponse.class)
                .actions();
    }

    private static List<String> leafKeys(List<ItemDefinition> items) {
        List<String> keys = new ArrayList<>();
        for (ItemDefinition def : items) {
            if (def.hasChildren()) {
                keys.addAll(leafKeys(def.children()));
            } else {
                keys.add(def.key());
            }
        }
        return keys;
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
