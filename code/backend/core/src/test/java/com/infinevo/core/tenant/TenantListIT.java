package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
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
 * W-65.1 spec §7: as staff bound to the Infinevo tenant, GET /tenants returns Acme (PAYROLL),
 * Globex (HRMS, PAYROLL) and Infinevo (none); GET /tenants/{acme} matches; unknown id is 404.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class TenantListIT extends AbstractIntegrationTest {

    private static final UUID PLATFORM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private UUID staffSub;
    private UUID acmeTenant;
    private UUID globexTenant;

    @BeforeEach
    void setUp() throws SQLException {
        // Platform staff user bound to Infinevo tenant holding platform-admin
        staffSub = UUID.randomUUID();
        UUID staffAccount = AuthzTestSchema.insertMember(PLATFORM_TENANT, staffSub, "staff@infinevo.test");
        UUID platformAdminRole = AuthzTestSchema.roleId(PLATFORM_TENANT, "platform-admin");
        AuthzTestSchema.grant(PLATFORM_TENANT, staffAccount, platformAdminRole);

        // Acme tenant with PAYROLL
        acmeTenant = AuthzTestSchema.insertTenant("Acme " + UUID.randomUUID());
        provisionSubscription(acmeTenant, "ACTIVE", "PAYROLL");

        // Globex tenant with HRMS and PAYROLL
        globexTenant = AuthzTestSchema.insertTenant("Globex " + UUID.randomUUID());
        provisionSubscription(globexTenant, "ACTIVE", "HRMS", "PAYROLL");
    }

    @Test
    @DisplayName("staff bound to Infinevo gets list containing Acme, Globex, and Infinevo with modules")
    void listTenants_asPlatformStaff_returnsAllTenants() throws Exception {
        MvcResult result = mvc.perform(as(PLATFORM_TENANT, staffSub, get("/api/v1/tenants")))
                .andExpect(status().isOk())
                .andReturn();

        List<TenantOverview> tenants =
                json.readValue(result.getResponse().getContentAsString(), new TypeReference<List<TenantOverview>>() {});

        assertThat(tenants).isNotEmpty();

        TenantOverview infinevoRow = tenants.stream()
                .filter(t -> t.tenantId().equals(PLATFORM_TENANT))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Infinevo platform tenant missing from list"));
        assertThat(infinevoRow.name()).isEqualTo("Infinevo");
        assertThat(infinevoRow.modules()).isEmpty();

        TenantOverview acmeRow = tenants.stream()
                .filter(t -> t.tenantId().equals(acmeTenant))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Acme tenant missing from list"));
        assertThat(acmeRow.modules()).containsExactly("PAYROLL");

        TenantOverview globexRow = tenants.stream()
                .filter(t -> t.tenantId().equals(globexTenant))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Globex tenant missing from list"));
        assertThat(globexRow.modules()).containsExactlyInAnyOrder("HRMS", "PAYROLL");
    }

    @Test
    @DisplayName("staff bound to Infinevo gets overview for existing tenant")
    void getTenantOverview_asPlatformStaff_matches() throws Exception {
        MvcResult result = mvc.perform(as(PLATFORM_TENANT, staffSub, get("/api/v1/tenants/" + acmeTenant)))
                .andExpect(status().isOk())
                .andReturn();

        TenantOverview acme = json.readValue(result.getResponse().getContentAsString(), TenantOverview.class);

        assertThat(acme.tenantId()).isEqualTo(acmeTenant);
        assertThat(acme.modules()).containsExactly("PAYROLL");
    }

    @Test
    @DisplayName("staff bound to Infinevo receives 404 TENANT_NOT_FOUND for unknown tenant id")
    void getTenantOverview_unknownId_returns404() throws Exception {
        UUID unknownTenant = UUID.randomUUID();
        mvc.perform(as(PLATFORM_TENANT, staffSub, get("/api/v1/tenants/" + unknownTenant)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    private void provisionSubscription(UUID tenantId, String status, String... modules) throws SQLException {
        UUID subId = UUID.randomUUID();
        try (Connection conn = AuthzTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription (id, tenant_id, status, started_on) VALUES (?, ?, ?, CURRENT_DATE) ON CONFLICT (tenant_id) DO NOTHING")) {
                ps.setObject(1, subId);
                ps.setObject(2, tenantId);
                ps.setString(3, status);
                ps.executeUpdate();
            }
            for (String mod : modules) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on) VALUES (?, ?, (SELECT id FROM core.subscription WHERE tenant_id = ?), ?, CURRENT_DATE) ON CONFLICT DO NOTHING")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, tenantId);
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
