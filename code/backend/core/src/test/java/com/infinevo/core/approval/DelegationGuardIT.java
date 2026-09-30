package com.infinevo.core.approval;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
 * Authorization guard integration tests for DelegationController (W-15.3, spec section 4).
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class DelegationGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeService employeeService;

    private UUID tenantId;
    private UUID employeeNoActionSub;
    private UUID delegatorSub;
    private UUID delegatorEmpId;
    private UUID delegateEmpId;

    @BeforeEach
    void setUp() throws Exception {
        tenantId = AuthzTestSchema.insertTenant("DelegationGuardIT " + UUID.randomUUID());
        TenantContext.set(tenantId);

        // 1. Employee without core.approval.delegate
        employeeNoActionSub = UUID.randomUUID();
        UUID empAccountId = AuthzTestSchema.insertMember(tenantId, employeeNoActionSub, "noaction@delegation.test");
        AuthzTestSchema.grant(tenantId, empAccountId, AuthzTestSchema.roleId(tenantId, "employee"));
        UUID noActionEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-401", "NoAction");
        employeeService.linkLogin(noActionEmpId, empAccountId);

        // 2. Delegator with tenant-admin (has core.approval.delegate)
        delegatorSub = UUID.randomUUID();
        UUID delegatorAccountId = AuthzTestSchema.insertMember(tenantId, delegatorSub, "delegator@delegation.test");
        AuthzTestSchema.grant(tenantId, delegatorAccountId, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
        delegatorEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-402", "Delegator");
        employeeService.linkLogin(delegatorEmpId, delegatorAccountId);

        // 3. Delegate employee
        delegateEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-403", "Delegate");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Caller without core.approval.delegate gets 403 on delegation endpoints")
    void callerWithoutDelegateActionGets403() throws Exception {
        DelegationCreateRequest createReq = new DelegationCreateRequest(
                delegateEmpId, LocalDate.now(), LocalDate.now().plusDays(5), "LEAVE");

        // POST /api/v1/approval-delegations -> 403
        mvc.perform(post("/api/v1/approval-delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq))
                        .with(jwt().jwt(b ->
                                b.subject(employeeNoActionSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        // GET /api/v1/approval-delegations -> 403
        mvc.perform(get("/api/v1/approval-delegations").with(jwt().jwt(b -> b.subject(employeeNoActionSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        // DELETE /api/v1/approval-delegations/{id} -> 403
        mvc.perform(delete("/api/v1/approval-delegations/" + UUID.randomUUID())
                        .with(jwt().jwt(b ->
                                b.subject(employeeNoActionSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Caller with core.approval.delegate can create, list, and delete own delegation")
    void callerWithDelegateActionCanManageDelegations() throws Exception {
        DelegationCreateRequest createReq = new DelegationCreateRequest(
                delegateEmpId, LocalDate.now(), LocalDate.now().plusDays(5), "LEAVE");

        // Create -> 201 Created
        String responseContent = mvc.perform(post("/api/v1/approval-delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq))
                        .with(jwt().jwt(b ->
                                b.subject(delegatorSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delegatorEmployeeId").value(delegatorEmpId.toString()))
                .andExpect(jsonPath("$.delegateEmployeeId").value(delegateEmpId.toString()))
                .andExpect(jsonPath("$.flowTypes").value("LEAVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DelegationResponse delegation = objectMapper.readValue(responseContent, DelegationResponse.class);
        UUID delegationId = delegation.id();

        // List -> 200 OK
        mvc.perform(get("/api/v1/approval-delegations").with(jwt().jwt(b -> b.subject(delegatorSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(delegationId.toString()));

        // Delete -> 204 No Content
        mvc.perform(delete("/api/v1/approval-delegations/" + delegationId)
                        .with(jwt().jwt(b ->
                                b.subject(delegatorSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNoContent());

        // List active on today should now be empty
        mvc.perform(get("/api/v1/approval-delegations?activeOn=" + LocalDate.now())
                        .with(jwt().jwt(b ->
                                b.subject(delegatorSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
