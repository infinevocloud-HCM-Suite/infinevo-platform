package com.infinevo.core.employee;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W-13.3, spec section 7 — {@code EmployeeListGuardIT}.
 *
 * <p>Guards on {@code GET /api/v1/employees}:
 *
 * <ul>
 *   <li>A user holding only {@code core.employee.read_own} (role {@code employee}) gets {@code 403}
 *       — the endpoint requires {@code core.employee.read}.
 *   <li>A user holding {@code core.employee.read} (role {@code payroll-officer}) gets {@code 200}.
 *   <li>A user with {@code core.employee.read} alone (role {@code payroll-officer}) and
 *       {@code includeDeleted=true} gets {@code 403} — the query parameter requires
 *       {@code core.employee.delete}.
 *   <li>A user with {@code core.employee.read} and {@code core.employee.delete} (role {@code hr})
 *       with {@code includeDeleted=true} gets {@code 200}.
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
class EmployeeListGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private UUID tenantId;
    private UUID employeeSub;
    private UUID payrollOfficerSub;
    private UUID hrSub;

    @BeforeEach
    void seed() throws Exception {
        tenantId = AuthzTestSchema.insertTenant("ListGuard " + UUID.randomUUID());
        TenantContext.set(tenantId);

        employeeSub = UUID.randomUUID();
        UUID employeeAccount = AuthzTestSchema.insertMember(tenantId, employeeSub, "emp@guard.test");
        AuthzTestSchema.grant(tenantId, employeeAccount, AuthzTestSchema.roleId(tenantId, "employee"));

        payrollOfficerSub = UUID.randomUUID();
        UUID poAccount = AuthzTestSchema.insertMember(tenantId, payrollOfficerSub, "po@guard.test");
        AuthzTestSchema.grant(tenantId, poAccount, AuthzTestSchema.roleId(tenantId, "payroll-officer"));

        hrSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(tenantId, hrSub, "hr@guard.test");
        AuthzTestSchema.grant(tenantId, hrAccount, AuthzTestSchema.roleId(tenantId, "hr"));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("core.employee.read_own alone gets 403 on GET /employees")
    void readOwnAloneGetsForbidden() throws Exception {
        mvc.perform(get("/api/v1/employees").with(jwt().jwt(b -> b.subject(employeeSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("core.employee.read gets 200 on GET /employees")
    void readGetsOk() throws Exception {
        mvc.perform(get("/api/v1/employees").with(jwt().jwt(b -> b.subject(payrollOfficerSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("core.employee.read alone with includeDeleted=true gets 403")
    void readAloneWithIncludeDeletedGetsForbidden() throws Exception {
        mvc.perform(get("/api/v1/employees").param("includeDeleted", "true").with(jwt().jwt(b -> b.subject(
                                payrollOfficerSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("core.employee.read + core.employee.delete with includeDeleted=true gets 200")
    void readPlusDeleteWithIncludeDeletedGetsOk() throws Exception {
        mvc.perform(get("/api/v1/employees")
                        .param("includeDeleted", "true")
                        .with(jwt().jwt(b -> b.subject(hrSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }
}
