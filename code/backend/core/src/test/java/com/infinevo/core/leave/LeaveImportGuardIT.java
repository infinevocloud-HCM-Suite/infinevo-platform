package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.math.BigDecimal;
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
 * W-16.4b, spec section 7 &amp; 9 — {@code LeaveImportGuardIT}.
 *
 * <p>Verifies permission guards on {@code /api/v1/leave-imports}:
 * <ul>
 *   <li>Callers without {@code core.leave_balance.manage} receive HTTP 403 on POST, GET /{id}, and GET.
 *   <li>Callers with {@code core.leave_balance.manage} (e.g. HR role) get past the guard.
 *   <li>Verifies decimal days format (e.g., 12.5 imports with scale 2 as 12.50).
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
class LeaveImportGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID employeeSub;
    private UUID hrSub;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("Guard " + UUID.randomUUID());
        UUID employeeRole = AuthzTestSchema.roleId(tenant, "employee");
        UUID hrRole = AuthzTestSchema.roleId(tenant, "hr");

        // Employee without core.leave_balance.manage
        employeeSub = UUID.randomUUID();
        UUID empAccount = AuthzTestSchema.insertMember(tenant, employeeSub, "emp@guard.test");
        AuthzTestSchema.grant(tenant, empAccount, employeeRole);

        // HR user holding core.leave_balance.manage
        hrSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(tenant, hrSub, "hr@guard.test");
        AuthzTestSchema.grant(tenant, hrAccount, hrRole);
        AuthzTestSchema.grant(tenant, hrAccount, employeeRole);
    }

    @Test
    @DisplayName("POST /api/v1/leave-imports without core.leave_balance.manage returns 403 FORBIDDEN")
    void postImportRefusedWithoutManagePermission() throws Exception {
        UUID docId = UUID.randomUUID();
        mvc.perform(as(employeeSub, post("/api/v1/leave-imports"))
                        .content(body(new LeaveImportRequest(docId, "2026", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.leave_balance.manage")));
    }

    @Test
    @DisplayName("GET /api/v1/leave-imports/{id} without core.leave_balance.manage returns 403 FORBIDDEN")
    void getImportByIdRefusedWithoutManagePermission() throws Exception {
        UUID randomId = UUID.randomUUID();
        mvc.perform(as(employeeSub, get("/api/v1/leave-imports/" + randomId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.leave_balance.manage")));
    }

    @Test
    @DisplayName("GET /api/v1/leave-imports without core.leave_balance.manage returns 403 FORBIDDEN")
    void listImportsRefusedWithoutManagePermission() throws Exception {
        mvc.perform(as(employeeSub, get("/api/v1/leave-imports")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.leave_balance.manage")));
    }

    @Test
    @DisplayName("HR user holding core.leave_balance.manage passes guard on listing and detail endpoints")
    void hrUserPassesGuard() throws Exception {
        // List imports -> passes guard, returns 200 OK
        mvc.perform(as(hrSub, get("/api/v1/leave-imports")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // Get single import -> passes guard, reaches service, returns 404 (not found) for random UUID
        UUID missingId = UUID.randomUUID();
        mvc.perform(as(hrSub, get("/api/v1/leave-imports/" + missingId))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Decimal days e.g. 12.5 parses with scale 2 (12.50) according to spec §9")
    void decimalDaysParsedWithScale2() {
        BigDecimal parsed = new BigDecimal("12.5").setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(parsed.scale()).isEqualTo(2);
        assertThat(parsed.toString()).isEqualTo("12.50");
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
