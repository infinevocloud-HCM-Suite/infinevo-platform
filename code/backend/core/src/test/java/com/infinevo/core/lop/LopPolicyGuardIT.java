package com.infinevo.core.lop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
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
 * W-18.1 — Authorization guard tests for loss-of-pay policy endpoints (W-18.1 §7).
 *
 * <p>PUT is 403 without core.lop_policy.manage;
 * GET without core.lop_policy.read is 403;
 * GET /basis with no policy is 409, not 200 with a default.
 */
@SpringBootTest(
        classes = PermissionGuardTestApp.class,
        properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class LopPolicyGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private UUID tenant;
    private UUID noPermSub;
    private UUID readerSub;
    private UUID managerSub;

    @BeforeEach
    void setUp() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("LopGuard " + UUID.randomUUID());

        // 1. User with no lop_policy actions
        noPermSub = UUID.randomUUID();
        UUID noPermAccount = AuthzTestSchema.insertMember(tenant, noPermSub, "noperm@guard.test");
        UUID noLopRole =
                AuthzTestSchema.insertRole(tenant, "no-lop-" + UUID.randomUUID(), "No Lop", "core.tenant.read");
        AuthzTestSchema.grant(tenant, noPermAccount, noLopRole);

        // 2. User holding core.lop_policy.read only
        readerSub = UUID.randomUUID();
        UUID readerAccount = AuthzTestSchema.insertMember(tenant, readerSub, "reader@guard.test");
        UUID readerRole =
                AuthzTestSchema.insertRole(tenant, "lop-reader-" + UUID.randomUUID(), "Reader", "core.lop_policy.read");
        AuthzTestSchema.grant(tenant, readerAccount, readerRole);

        // 3. User holding core.lop_policy.manage and core.lop_policy.read
        managerSub = UUID.randomUUID();
        UUID managerAccount = AuthzTestSchema.insertMember(tenant, managerSub, "manager@guard.test");
        UUID managerRole = AuthzTestSchema.insertRole(
                tenant,
                "lop-manager-" + UUID.randomUUID(),
                "Manager",
                "core.lop_policy.read",
                "core.lop_policy.manage");
        AuthzTestSchema.grant(tenant, managerAccount, managerRole);

        // Seed initial policy for the tenant so getPolicy returns 200
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.lop_policy (id, tenant_id, working_day_basis, weekends_payable,
                            holidays_payable, lop_rounding, effective_from)
                        VALUES (?, ?, 'ACTUAL_DAYS', true, true, 'HALF_UP_2', '1900-01-01')
                        """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("GET /api/v1/lop-policy without core.lop_policy.read is 403")
    void getPolicy_withoutRead_forbidden() throws Exception {
        mvc.perform(as(noPermSub, get("/api/v1/lop-policy")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/lop-policy with core.lop_policy.read is 200")
    void getPolicy_withRead_ok() throws Exception {
        mvc.perform(as(readerSub, get("/api/v1/lop-policy"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/lop-policy without core.lop_policy.manage is 403")
    void putPolicy_withoutManage_forbidden() throws Exception {
        LopPolicyRequest req = new LopPolicyRequest(
                WorkingDayBasis.ACTUAL_DAYS, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        mvc.perform(as(readerSub, put("/api/v1/lop-policy")).content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PUT /api/v1/lop-policy with core.lop_policy.manage is 200")
    void putPolicy_withManage_ok() throws Exception {
        LopPolicyRequest req = new LopPolicyRequest(
                WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        mvc.perform(as(managerSub, put("/api/v1/lop-policy")).content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDayBasis").value("FIXED_30"));
    }

    @Test
    @DisplayName("GET /api/v1/lop-policy/basis with no policy in force is 409 Conflict, not 200 with default")
    void getBasis_noPolicyInForce_is409Conflict() throws Exception {
        // Delete all policies for this tenant so none is in force
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM core.lop_policy WHERE tenant_id = ?")) {
            ps.setObject(1, tenant);
            ps.executeUpdate();
        }

        mvc.perform(as(readerSub, get("/api/v1/lop-policy/basis").param("period", "2026-07")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private MockHttpServletRequestBuilder as(UUID subject, MockHttpServletRequestBuilder builder) {
        return builder.with(jwt().jwt(jwt -> jwt.subject(subject.toString())
                        .claim("email", subject + "@guard.test")
                        .claim("name", "Guard User")))
                .header("X-Tenant-ID", tenant.toString())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
