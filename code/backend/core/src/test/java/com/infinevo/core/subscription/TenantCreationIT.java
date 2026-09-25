package com.infinevo.core.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.tenant.TenantRequest;
import com.infinevo.core.tenant.TenantResponse;
import com.infinevo.core.tenant.TenantService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for tenant creation with module selection and locale defaults (W-12.1).
 */
@SpringBootTest(classes = SubscriptionTestApp.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantCreationIT extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private EntitlementReadService entitlementReadService;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PermissionService permissionService;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void applySchema() throws Exception {
        SubscriptionTestSchema.apply();
    }

    @Test
    @DisplayName(
            "Creating tenant with [PAYROLL] yields exactly one module row, EntitlementReadService omits HRMS, and defaults are applied")
    void createTenant_withPayroll_yieldsOneModuleAndOmitsHrms() throws SQLException {
        TenantRequest request = new TenantRequest("Acme Branch", null, null, null, Set.of(PlatformModule.PAYROLL));
        TenantResponse response = tenantService.provisionTenant(request);

        assertThat(response).isNotNull();
        UUID tenantId = response.tenantId();
        assertThat(tenantId).isNotNull();
        assertThat(response.countryCode()).isEqualTo("IN");
        assertThat(response.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(response.leaveYearStartMonth()).isEqualTo((short) 4);
        assertThat(response.modules()).containsExactly(PlatformModule.PAYROLL);

        // Verify core.tenant defaults in DB
        try (Connection conn = SubscriptionTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT country_code, timezone, leave_year_start_month FROM core.tenant WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("country_code")).isEqualTo("IN");
                assertThat(rs.getString("timezone")).isEqualTo("Asia/Kolkata");
                assertThat(rs.getShort("leave_year_start_month")).isEqualTo((short) 4);
            }
        }

        // Verify exactly one module in core.subscription_module
        try (Connection conn = SubscriptionTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT module, revoked_on FROM core.subscription_module WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("module")).isEqualTo("PAYROLL");
                assertThat(rs.getDate("revoked_on")).isNull();
                assertThat(rs.next()).isFalse();
            }
        }

        // Verify EntitlementReadService returns PAYROLL and omits HRMS
        TenantContext.set(tenantId);
        try {
            Set<PlatformModule> modules = entitlementReadService.modulesOf(tenantId);
            assertThat(modules).containsExactly(PlatformModule.PAYROLL);
            assertThat(modules).doesNotContain(PlatformModule.HRMS);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName(
            "Creating tenant with custom country_code, timezone, and leave_year_start_month preserves given values")
    void createTenant_withCustomLocale_preservesGivenValues() throws SQLException {
        TenantRequest request = new TenantRequest(
                "Globex Europe", "GB", "Europe/London", (short) 1, Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));
        TenantResponse response = tenantService.provisionTenant(request);

        UUID tenantId = response.tenantId();
        assertThat(response.countryCode()).isEqualTo("GB");
        assertThat(response.timezone()).isEqualTo("Europe/London");
        assertThat(response.leaveYearStartMonth()).isEqualTo((short) 1);
        assertThat(response.modules()).containsExactlyInAnyOrder(PlatformModule.HRMS, PlatformModule.PAYROLL);

        // Verify DB row
        try (Connection conn = SubscriptionTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT country_code, timezone, leave_year_start_month FROM core.tenant WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("country_code")).isEqualTo("GB");
                assertThat(rs.getString("timezone")).isEqualTo("Europe/London");
                assertThat(rs.getShort("leave_year_start_month")).isEqualTo((short) 1);
            }
        }

        // Verify EntitlementReadService holds both modules
        TenantContext.set(tenantId);
        try {
            Set<PlatformModule> modules = entitlementReadService.modulesOf(tenantId);
            assertThat(modules).containsExactlyInAnyOrder(PlatformModule.HRMS, PlatformModule.PAYROLL);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Invalid timezone 'Mars/Olympus' fails with 400 VALIDATION_FAILED")
    void invalidTimezone_failsValidation() throws Exception {
        TenantRequest request = new TenantRequest("Alien Corp", "IN", "Mars/Olympus", (short) 4, Set.of());

        assertThatThrownBy(() -> tenantService.provisionTenant(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timezone");

        // Over HTTP
        mvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiError.VALIDATION_FAILED.code()));
    }

    @Test
    @DisplayName("Invalid leave_year_start_month '13' fails with 400 VALIDATION_FAILED")
    void invalidLeaveYearStartMonth_failsValidation() throws Exception {
        TenantRequest request = new TenantRequest("Year Corp", "IN", "Asia/Kolkata", (short) 13, Set.of());

        assertThatThrownBy(() -> tenantService.provisionTenant(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leave_year_start_month must be between 1 and 12");

        // Over HTTP
        mvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiError.VALIDATION_FAILED.code()));
    }
}
