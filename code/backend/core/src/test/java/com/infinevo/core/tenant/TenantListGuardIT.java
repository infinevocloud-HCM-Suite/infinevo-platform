package com.infinevo.core.tenant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-65.1 spec §7: admin.acme (holds core.tenant.read, not provision) gets 403 on both endpoints;
 * a test user granted core.tenant.provision inside Acme still gets 403 — proving the tenant check.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class TenantListGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private UUID acmeTenant;
    private UUID adminSub;
    private UUID provisionerSub;

    @BeforeEach
    void setUp() throws SQLException {
        acmeTenant = AuthzTestSchema.insertTenant("Acme Guard " + UUID.randomUUID());

        // 1. admin.acme (holds tenant-admin: has core.tenant.read, but not core.tenant.provision)
        adminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(acmeTenant, adminSub, "admin@acme.guard.test");
        AuthzTestSchema.grant(acmeTenant, adminAccount, AuthzTestSchema.roleId(acmeTenant, "tenant-admin"));

        // 2. Customer user who holds core.tenant.provision inside Acme
        provisionerSub = UUID.randomUUID();
        UUID provisionerAccount =
                AuthzTestSchema.insertMember(acmeTenant, provisionerSub, "provisioner@acme.guard.test");
        UUID provisionerRole =
                AuthzTestSchema.insertRole(acmeTenant, "custom-provisioner", "Provisioner", "core.tenant.provision");
        AuthzTestSchema.grant(acmeTenant, provisionerAccount, provisionerRole);
    }

    @Test
    @DisplayName("admin.acme lacking core.tenant.provision gets 403 on GET /tenants and GET /tenants/{id}")
    void adminAcme_lacksProvisionAction_forbiddenOnBothEndpoints() throws Exception {
        mvc.perform(as(acmeTenant, adminSub, get("/api/v1/tenants")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(as(acmeTenant, adminSub, get("/api/v1/tenants/" + acmeTenant)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("customer user holding core.tenant.provision inside Acme still gets 403 (tenant check)")
    void customerUserWithProvisionAction_notPlatformTenant_forbiddenOnBothEndpoints() throws Exception {
        mvc.perform(as(acmeTenant, provisionerSub, get("/api/v1/tenants")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(as(acmeTenant, provisionerSub, get("/api/v1/tenants/" + acmeTenant)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
