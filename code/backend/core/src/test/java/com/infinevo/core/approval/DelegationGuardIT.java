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
                .andExpect(jsonPath("$.revocable").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DelegationResponse delegation = objectMapper.readValue(responseContent, DelegationResponse.class);
        UUID delegationId = delegation.id();

        // List -> 200 OK
        mvc.perform(get("/api/v1/approval-delegations").with(jwt().jwt(b -> b.subject(delegatorSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(delegationId.toString()))
                .andExpect(jsonPath("$[0].revocable").value(true));

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

    @Test
    @DisplayName("With no employeeId the list is the caller's own, and only the delegator's rows are revocable")
    void listDefaultsToTheCallersOwnAndMarksWhoMayRevoke() throws Exception {
        // A second admin, who is the delegate of the first one's delegation and makes one of their own.
        UUID delegateSub = UUID.randomUUID();
        UUID delegateAccountId = AuthzTestSchema.insertMember(tenantId, delegateSub, "delegate@delegation.test");
        AuthzTestSchema.grant(tenantId, delegateAccountId, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
        employeeService.linkLogin(delegateEmpId, delegateAccountId);

        // A third admin whose delegation involves neither of them.
        UUID strangerSub = UUID.randomUUID();
        UUID strangerAccountId = AuthzTestSchema.insertMember(tenantId, strangerSub, "stranger@delegation.test");
        AuthzTestSchema.grant(tenantId, strangerAccountId, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
        UUID strangerEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-404", "Stranger");
        employeeService.linkLogin(strangerEmpId, strangerAccountId);
        UUID bystanderEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-405", "Bystander");

        String mine = createDelegationAs(delegatorSub, delegateEmpId);
        String strangers = createDelegationAs(strangerSub, bystanderEmpId);

        // The delegator sees their own row, revocable, and not the stranger's.
        mvc.perform(get("/api/v1/approval-delegations").with(jwt().jwt(b -> b.subject(delegatorSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine))
                .andExpect(jsonPath("$[0].revocable").value(true));

        // The delegate sees the same row, because it was made to them - but may not revoke it.
        mvc.perform(get("/api/v1/approval-delegations").with(jwt().jwt(b -> b.subject(delegateSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine))
                .andExpect(jsonPath("$[0].revocable").value(false));

        // ... and the endpoint agrees with the flag.
        mvc.perform(delete("/api/v1/approval-delegations/" + mine).with(jwt().jwt(b -> b.subject(delegateSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/approval-delegations").with(jwt().jwt(b -> b.subject(strangerSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(strangers));
    }

    private String createDelegationAs(UUID sub, UUID delegateId) throws Exception {
        DelegationCreateRequest request = new DelegationCreateRequest(
                delegateId, LocalDate.now(), LocalDate.now().plusDays(5), "LEAVE");
        String body = mvc.perform(post("/api/v1/approval-delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(b -> b.subject(sub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(body, DelegationResponse.class).id().toString();
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
