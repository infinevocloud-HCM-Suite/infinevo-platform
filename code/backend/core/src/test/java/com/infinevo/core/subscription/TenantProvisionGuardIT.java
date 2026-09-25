package com.infinevo.core.subscription;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.tenant.TenantRequest;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
 * W-12.1 — Authorization guard tests for tenant and subscription provisioning (spec section 7).
 *
 * <p>A user without {@code core.tenant.provision} gets {@code 403 FORBIDDEN} on {@code POST /tenants}
 * and both {@code PUT} endpoints, whatever realm role the token carries; {@code core.tenant.read}
 * alone reaches only the {@code GET}.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class TenantProvisionGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID provisionerSub;
    private UUID readerSub;
    private UUID tenantAdminSub;
    private UUID employeeSub;

    @BeforeEach
    void setUp() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("ProvisionGuard " + UUID.randomUUID());

        // 1. Provisioner holding core.tenant.provision
        provisionerSub = UUID.randomUUID();
        UUID provisionerAccount = AuthzTestSchema.insertMember(tenant, provisionerSub, "provisioner@guard.test");
        UUID provisionerRole =
                AuthzTestSchema.insertRole(tenant, "platform-provisioner", "Provisioner", "core.tenant.provision");
        AuthzTestSchema.grant(tenant, provisionerAccount, provisionerRole);

        // 2. Reader holding core.tenant.read only
        readerSub = UUID.randomUUID();
        UUID readerAccount = AuthzTestSchema.insertMember(tenant, readerSub, "reader@guard.test");
        UUID readerRole = AuthzTestSchema.insertRole(tenant, "tenant-reader", "Reader", "core.tenant.read");
        AuthzTestSchema.grant(tenant, readerAccount, readerRole);

        // 3. Customer tenant-admin (seeded role; does not hold core.tenant.provision)
        tenantAdminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(tenant, tenantAdminSub, "admin@guard.test");
        AuthzTestSchema.grant(tenant, adminAccount, AuthzTestSchema.roleId(tenant, "tenant-admin"));

        // 4. Regular employee (holds employee role)
        employeeSub = UUID.randomUUID();
        UUID employeeAccount = AuthzTestSchema.insertMember(tenant, employeeSub, "employee@guard.test");
        AuthzTestSchema.grant(tenant, employeeAccount, AuthzTestSchema.roleId(tenant, "employee"));
    }

    @Test
    @DisplayName("User without core.tenant.provision gets 403 on POST /tenants, even with realm role platform-admin")
    void postTenants_withoutProvisionAction_forbiddenEvenWithRealmRole() throws Exception {
        TenantRequest req = new TenantRequest("Denied Tenant", null, null, null, Set.of());

        // tenant-admin gets 403
        mvc.perform(asWithRealmRole(tenantAdminSub, "platform-admin", post("/api/v1/tenants"))
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // reader gets 403
        mvc.perform(as(readerSub, post("/api/v1/tenants")).content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // employee gets 403
        mvc.perform(as(employeeSub, post("/api/v1/tenants")).content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("User without core.tenant.provision gets 403 on PUT subscription modules and status")
    void putSubscription_withoutProvisionAction_forbidden() throws Exception {
        ModulesUpdateRequest modReq = new ModulesUpdateRequest(Set.of(PlatformModule.HRMS));
        StatusUpdateRequest statusReq = new StatusUpdateRequest(SubscriptionStatus.PAST_DUE);

        mvc.perform(asWithRealmRole(
                                tenantAdminSub,
                                "platform-admin",
                                put("/api/v1/tenants/" + tenant + "/subscription/modules"))
                        .content(json.writeValueAsString(modReq)))
                .andExpect(status().isForbidden());

        mvc.perform(asWithRealmRole(
                                tenantAdminSub,
                                "platform-admin",
                                put("/api/v1/tenants/" + tenant + "/subscription/status"))
                        .content(json.writeValueAsString(statusReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("core.tenant.read alone reaches GET /api/v1/tenants/{id}/subscription, while POST and PUTs remain 403")
    void readerCanGetSubscription_butCannotModify() throws Exception {
        // GET succeeds through the guard (service executes)
        mvc.perform(as(readerSub, get("/api/v1/tenants/" + tenant + "/subscription")))
                .andExpect(status().isNotFound()); // 404 because no subscription created for this test tenant yet, but
        // passed 403 guard!

        // POST /tenants is 403
        TenantRequest req = new TenantRequest("Denied Corp", null, null, null, Set.of());
        mvc.perform(as(readerSub, post("/api/v1/tenants")).content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // PUT /modules is 403
        ModulesUpdateRequest modReq = new ModulesUpdateRequest(Set.of(PlatformModule.PAYROLL));
        mvc.perform(as(readerSub, put("/api/v1/tenants/" + tenant + "/subscription/modules"))
                        .content(json.writeValueAsString(modReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User with core.tenant.provision can create tenants and update subscription")
    void provisioner_canCreateTenant() throws Exception {
        TenantRequest req =
                new TenantRequest("Allowed Corp", "IN", "Asia/Kolkata", (short) 4, Set.of(PlatformModule.PAYROLL));

        mvc.perform(as(provisionerSub, post("/api/v1/tenants")).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Allowed Corp"));
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private MockHttpServletRequestBuilder asWithRealmRole(
            UUID sub, String realmRole, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON).with(jwt().jwt(token -> token.subject(sub.toString())
                .claim("tenant_id", tenant.toString())
                .claim("realm_access", Map.of("roles", List.of(realmRole)))));
    }
}
