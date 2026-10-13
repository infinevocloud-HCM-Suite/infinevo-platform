package com.infinevo.core.org;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W-14.2, spec section 7 — {@code ReportingLineGuardIT}.
 *
 * <ul>
 *   <li>{@code PUT} without {@code core.reporting_line.manage} gets {@code 403}.
 *   <li>{@code GET} with {@code core.org.read} alone gets {@code 200}.
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
class ReportingLineGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID hrSub;
    private UUID adminSub;
    private UUID employeeSub;
    private UUID emp1Id;
    private UUID emp2Id;

    @BeforeEach
    void seed() throws Exception {
        tenantId = AuthzTestSchema.insertTenant("LineGuard " + UUID.randomUUID());
        TenantContext.set(tenantId);

        // Create two employees in tenantId (using AuthzTestSchema's database)
        emp1Id = AuthzTestSchema.insertEmployee(tenantId, "GUARD-RL-1", "EmpOne");
        emp2Id = AuthzTestSchema.insertEmployee(tenantId, "GUARD-RL-2", "EmpTwo");

        hrSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(tenantId, hrSub, "hr@lineguard.test");
        AuthzTestSchema.grant(tenantId, hrAccount, AuthzTestSchema.roleId(tenantId, "hr"));

        adminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(tenantId, adminSub, "admin@lineguard.test");
        AuthzTestSchema.grant(tenantId, adminAccount, AuthzTestSchema.roleId(tenantId, "tenant-admin"));

        employeeSub = UUID.randomUUID();
        UUID empAccount = AuthzTestSchema.insertMember(tenantId, employeeSub, "emp@lineguard.test");
        AuthzTestSchema.grant(tenantId, empAccount, AuthzTestSchema.roleId(tenantId, "employee"));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET with core.org.read alone gets 200")
    void getWithOrgReadGets200() throws Exception {
        mvc.perform(get("/api/v1/employees/" + emp1Id + "/reporting-line")
                        .with(jwt().jwt(b -> b.subject(hrSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT without core.reporting_line.manage gets 403")
    void putWithoutManagePermissionGets403() throws Exception {
        ReportingLineRequest body = new ReportingLineRequest(emp2Id, ReportingLineKind.PRIMARY, LocalDate.now(), null);

        mvc.perform(put("/api/v1/employees/" + emp1Id + "/reporting-line")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b ->
                                b.subject(employeeSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT with hr role (core.reporting_line.manage) gets 200")
    void putWithHrRoleGets200() throws Exception {
        ReportingLineRequest body = new ReportingLineRequest(emp2Id, ReportingLineKind.PRIMARY, LocalDate.now(), null);

        mvc.perform(put("/api/v1/employees/" + emp1Id + "/reporting-line")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(hrSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT with core.reporting_line.manage gets 200")
    void putWithManagePermissionGets200() throws Exception {
        ReportingLineRequest body = new ReportingLineRequest(emp2Id, ReportingLineKind.PRIMARY, LocalDate.now(), null);

        mvc.perform(put("/api/v1/employees/" + emp1Id + "/reporting-line")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }
}
