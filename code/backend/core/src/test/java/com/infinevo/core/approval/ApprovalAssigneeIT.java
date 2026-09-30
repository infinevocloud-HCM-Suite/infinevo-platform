package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * W-15.2, spec section 7 & section 13 — {@code ApprovalAssigneeIT}.
 *
 * <p>Proves authorization and attribution rules:
 * <ul>
 *   <li>A user without {@code core.approval.decide} gets 403 on all three endpoints.
 *   <li>A user holding {@code core.approval.decide} but not assigned to the step is refused (403).
 *   <li>Self-approval succeeds and is recorded where the requester is also the assigned approver (decision 1).
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
class ApprovalAssigneeIT extends AbstractIntegrationTest {

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
    private EmployeeService employeeService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private org.springframework.transaction.support.TransactionTemplate tx;

    private UUID tenantId;
    private UUID employeeSub;
    private UUID adminSub;
    private UUID otherAdminSub;
    private UUID empAccountId;
    private UUID adminAccountId;
    private UUID otherAdminAccountId;
    private UUID empEmployeeId;
    private UUID adminEmployeeId;
    private UUID otherAdminEmployeeId;

    @BeforeEach
    void setUp() throws Exception {
        tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        tenantId = AuthzTestSchema.insertTenant("ApprovalAssignee " + UUID.randomUUID());
        TenantContext.set(tenantId);

        // 1. Employee with role 'employee' (no core.approval.decide)
        employeeSub = UUID.randomUUID();
        empAccountId = AuthzTestSchema.insertMember(tenantId, employeeSub, "emp@approvalassignee.test");
        AuthzTestSchema.grant(tenantId, empAccountId, AuthzTestSchema.roleId(tenantId, "employee"));
        empEmployeeId = AuthzTestSchema.insertEmployee(tenantId, "EMP-201", "Regular");
        employeeService.linkLogin(empEmployeeId, empAccountId);

        // 2. Admin with role 'tenant-admin' (holds core.approval.decide)
        adminSub = UUID.randomUUID();
        adminAccountId = AuthzTestSchema.insertMember(tenantId, adminSub, "admin@approvalassignee.test");
        AuthzTestSchema.grant(tenantId, adminAccountId, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
        adminEmployeeId = AuthzTestSchema.insertEmployee(tenantId, "EMP-202", "Admin");
        employeeService.linkLogin(adminEmployeeId, adminAccountId);

        // 3. Other Admin with role 'tenant-admin' (holds core.approval.decide, but not assigned)
        otherAdminSub = UUID.randomUUID();
        otherAdminAccountId = AuthzTestSchema.insertMember(tenantId, otherAdminSub, "otheradmin@approvalassignee.test");
        AuthzTestSchema.grant(tenantId, otherAdminAccountId, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
        otherAdminEmployeeId = AuthzTestSchema.insertEmployee(tenantId, "EMP-203", "OtherAdmin");
        employeeService.linkLogin(otherAdminEmployeeId, otherAdminAccountId);

        // Configure single-step REGULARIZATION assigned to adminEmployeeId
        definitionService.saveDefinition(
                tenantId,
                ApprovalFlowType.REGULARIZATION,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(
                                ApproverKind.NAMED_EMPLOYEE, adminEmployeeId.toString(), 3, false))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Caller without core.approval.decide gets 403 on all three endpoints")
    void callerWithoutDecideActionGets403() throws Exception {
        UUID fakeInstanceId = UUID.randomUUID();
        UUID fakeStepId = UUID.randomUUID();

        // 1. GET /pending -> 403
        mvc.perform(get("/api/v1/approvals/pending").with(jwt().jwt(b -> b.subject(employeeSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        // 2. GET /{instanceId} -> 403
        mvc.perform(get("/api/v1/approvals/" + fakeInstanceId).with(jwt().jwt(b -> b.subject(employeeSub.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        // 3. POST /steps/{stepId}/decide -> 403
        ApprovalDecideRequest body = new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Attempt");
        mvc.perform(post("/api/v1/approvals/steps/" + fakeStepId + "/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b ->
                                b.subject(employeeSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Caller holding core.approval.decide but not assigned to the step is refused (403)")
    void callerNotAssignedIsRefused() throws Exception {
        SubjectRef subject = new SubjectRef("core.attendance_regularization", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.REGULARIZATION, subject, empEmployeeId);

        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId));
        ApprovalStep step = steps.get(0);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(adminEmployeeId);

        // otherAdminSub has core.approval.decide, but is NOT the assignee
        ApprovalDecideRequest body = new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Sneak decision");
        mvc.perform(post("/api/v1/approvals/steps/" + step.getId() + "/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b ->
                                b.subject(otherAdminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        // Assigned admin decides successfully
        ApprovalDecideRequest validBody = new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Authorized decision");
        mvc.perform(post("/api/v1/approvals/steps/" + step.getId() + "/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody))
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Self-approval succeeds and is recorded when requester and approver match (decision 1)")
    void selfApprovalSucceedsAndIsRecorded() throws Exception {
        // Start approval where adminEmployeeId is the requester, and the step is assigned to adminEmployeeId
        SubjectRef subject = new SubjectRef("core.attendance_regularization", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.REGULARIZATION, subject, adminEmployeeId);

        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId));
        ApprovalStep step = steps.get(0);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(adminEmployeeId);

        ApprovalDecideRequest body = new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Self approved");
        mvc.perform(post("/api/v1/approvals/steps/" + step.getId() + "/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());

        TenantContext.set(tenantId);
        ApprovalStep decidedStep =
                tx.execute(status -> stepRepository.findById(step.getId()).orElseThrow());
        assertThat(decidedStep.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(decidedStep.getAssigneeEmployeeId()).isEqualTo(adminEmployeeId);
    }

    @Test
    @DisplayName(
            "Request with no manager yields unassigned step surfaced in pending list to admin holding core.approval.manage")
    void requestWithNoManagerIsSurfacedInPendingListAndDecidableByAdmin() throws Exception {
        // empEmployeeId has no manager in reporting line
        definitionService.saveDefinition(
                tenantId,
                ApprovalFlowType.LEAVE,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER))));

        SubjectRef subject = new SubjectRef("core.leave_request", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.LEAVE, subject, empEmployeeId);

        TenantContext.set(tenantId);
        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId));
        assertThat(steps).hasSize(1);
        ApprovalStep step = steps.get(0);
        assertThat(step.getAssigneeEmployeeId()).isNull(); // unassignable / no manager

        // Admin holding core.approval.manage sees the unassigned step in GET /pending
        mvc.perform(get("/api/v1/approvals/pending")
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].id")
                        .value(step.getId().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.content[0].flowType")
                        .value("LEAVE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.content[0].subjectEmployeeId")
                        .value(empEmployeeId.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.content[0].itemId")
                        .value(subject.id().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.content[0].summary")
                        .isNotEmpty());

        // Admin decides the unassigned step directly
        ApprovalDecideRequest body =
                new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Admin decided unassigned step");
        mvc.perform(post("/api/v1/approvals/steps/" + step.getId() + "/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());

        TenantContext.set(tenantId);
        ApprovalStep decided =
                tx.execute(status -> stepRepository.findById(step.getId()).orElseThrow());
        assertThat(decided.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(decided.getAssigneeEmployeeId()).isEqualTo(adminEmployeeId);
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
