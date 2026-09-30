package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import java.util.List;
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
 * W-15.3, spec section 7 — {@code ReassignIT}.
 *
 * <p>Proves that POST /approvals/{id}/reassign with core.approval.manage moves the step and the
 * history shows it; without the code it is 403; the new assignee can then decide.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ReassignIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private ApprovalInstanceRepository instanceRepository;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private org.springframework.transaction.support.TransactionTemplate tx;

    private UUID tenantId;
    private UUID employeeSub;
    private UUID adminSub;
    private UUID newApproverSub;
    private UUID requesterEmpId;
    private UUID originalApproverEmpId;
    private UUID newApproverEmpId;

    @BeforeEach
    void setUp() throws Exception {
        tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        tenantId = AuthzTestSchema.insertTenant("ReassignIT " + UUID.randomUUID());
        TenantContext.set(tenantId);

        // 1. Employee without core.approval.manage
        employeeSub = UUID.randomUUID();
        UUID empAccountId = AuthzTestSchema.insertMember(tenantId, employeeSub, "emp@reassign.test");
        AuthzTestSchema.grant(tenantId, empAccountId, AuthzTestSchema.roleId(tenantId, "employee"));
        requesterEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-301", "Requester");
        employeeService.linkLogin(requesterEmpId, empAccountId);

        // 2. Original approver
        originalApproverEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-302", "OriginalApprover");

        // 3. Admin with core.approval.manage (tenant-admin)
        adminSub = UUID.randomUUID();
        UUID adminAccountId = AuthzTestSchema.insertMember(tenantId, adminSub, "admin@reassign.test");
        AuthzTestSchema.grant(tenantId, adminAccountId, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
        UUID adminEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-303", "Admin");
        employeeService.linkLogin(adminEmpId, adminAccountId);

        // 4. New approver with tenant-admin role (has core.approval.decide)
        newApproverSub = UUID.randomUUID();
        UUID newApproverAccountId = AuthzTestSchema.insertMember(tenantId, newApproverSub, "newapprover@reassign.test");
        AuthzTestSchema.grant(tenantId, newApproverAccountId, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
        newApproverEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-304", "NewApprover");
        employeeService.linkLogin(newApproverEmpId, newApproverAccountId);

        // Configure single step assigned to originalApproverEmpId
        definitionService.saveDefinition(
                tenantId,
                ApprovalFlowType.LEAVE,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(
                                ApproverKind.NAMED_EMPLOYEE, originalApproverEmpId.toString(), 3, false))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "POST /approvals/{id}/reassign with core.approval.manage moves step, shows in history, allows new assignee to decide")
    void reassignMovesStepAndEnablesDecision() throws Exception {
        SubjectRef subject = new SubjectRef("core.leave_request", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.LEAVE, subject, requesterEmpId);

        // 1. Without core.approval.manage -> 403 Forbidden
        ApprovalReassignRequest reassignReq = new ApprovalReassignRequest(newApproverEmpId, "Approver left company");
        mvc.perform(post("/api/v1/approvals/" + instanceId + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignReq))
                        .with(jwt().jwt(b ->
                                b.subject(employeeSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        // 2. With core.approval.manage -> 200 OK
        mvc.perform(post("/api/v1/approvals/" + instanceId + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignReq))
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeEmployeeId").value(newApproverEmpId.toString()))
                .andExpect(jsonPath("$.reassignedFromEmployeeId").value(originalApproverEmpId.toString()))
                .andExpect(jsonPath("$.reassignReason").value("Approver left company"));

        // 3. History endpoint shows the reassignment
        mvc.perform(get("/api/v1/approvals/" + instanceId + "/history")
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].assigneeEmployeeId").value(newApproverEmpId.toString()))
                .andExpect(jsonPath("$.steps[0].reassignedFromEmployeeId").value(originalApproverEmpId.toString()))
                .andExpect(jsonPath("$.steps[0].reassignReason").value("Approver left company"));

        // 4. New assignee can now decide the step
        TenantContext.set(tenantId);
        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId));
        UUID stepId = steps.get(0).getId();

        ApprovalDecideRequest decideReq =
                new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved by new assignee");
        mvc.perform(post("/api/v1/approvals/steps/" + stepId + "/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideReq))
                        .with(jwt().jwt(b ->
                                b.subject(newApproverSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());

        TenantContext.set(tenantId);
        ApprovalInstance completed =
                tx.execute(status -> instanceRepository.findById(instanceId).orElseThrow());
        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.APPROVED);
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
