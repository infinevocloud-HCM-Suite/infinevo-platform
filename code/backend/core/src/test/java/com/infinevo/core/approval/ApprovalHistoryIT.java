package com.infinevo.core.approval;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.time.Instant;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W-15.3, spec section 7 — {@code ApprovalHistoryIT}.
 *
 * <p>Proves that the history endpoint shows a delegated, an escalated and a reassigned decision
 * distinctly, and that core.approval.read suffices to read it.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ApprovalHistoryIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private ApprovalInstanceRepository instanceRepository;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private org.springframework.transaction.support.TransactionTemplate tx;

    private UUID tenantId;
    private UUID noPermUserSub;
    private UUID readerUserSub;

    @BeforeEach
    void setUp() throws Exception {
        tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        tenantId = AuthzTestSchema.insertTenant("ApprovalHistoryIT " + UUID.randomUUID());
        TenantContext.set(tenantId);

        // User without core.approval.read
        noPermUserSub = UUID.randomUUID();
        UUID noPermAccountId = AuthzTestSchema.insertMember(tenantId, noPermUserSub, "noperm@history.test");
        AuthzTestSchema.grant(tenantId, noPermAccountId, AuthzTestSchema.roleId(tenantId, "employee"));

        // User with custom role holding only core.approval.read
        readerUserSub = UUID.randomUUID();
        UUID readerAccountId = AuthzTestSchema.insertMember(tenantId, readerUserSub, "reader@history.test");
        UUID roleId = AuthzTestSchema.insertRole(tenantId, "history-reader", "History Reader", "core.approval.read");
        AuthzTestSchema.grant(tenantId, readerAccountId, roleId);
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        // These tests share a database with the employee tests, which delete employees; approval rows
        // left behind would block that delete through approval_instance's foreign key.
        ApprovalTestSchema.clearDefinitions();
    }

    @Test
    @DisplayName(
            "the history endpoint shows delegated, escalated and reassigned decisions distinctly; core.approval.read suffices")
    void historyShowsDelegatedEscalatedAndReassignedDistinctly() throws Exception {
        UUID delegatorId = AuthzTestSchema.insertEmployee(tenantId, "EMP-H01", "Delegator");
        UUID delegateId = AuthzTestSchema.insertEmployee(tenantId, "EMP-H02", "Delegate");
        UUID escalatedFromId = AuthzTestSchema.insertEmployee(tenantId, "EMP-H03", "EscalatedFrom");
        UUID escalationAssigneeId = AuthzTestSchema.insertEmployee(tenantId, "EMP-H04", "EscalationAssignee");
        UUID reassignedFromId = AuthzTestSchema.insertEmployee(tenantId, "EMP-H05", "ReassignedFrom");
        UUID reassignedToId = AuthzTestSchema.insertEmployee(tenantId, "EMP-H06", "ReassignedTo");
        UUID requesterId = AuthzTestSchema.insertEmployee(tenantId, "EMP-H07", "Requester");

        ApprovalDefinitionResponse defResp = definitionService.saveDefinition(
                tenantId,
                ApprovalFlowType.LEAVE,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(
                                new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false),
                                new ApprovalStepDefinition(ApproverKind.INDIRECT_MANAGER, null, 3, false),
                                new ApprovalStepDefinition(
                                        ApproverKind.NAMED_EMPLOYEE, reassignedToId.toString(), 3, false))));

        // Create an instance with 3 steps
        UUID instanceId = tx.execute(status -> {
            ApprovalInstance instance = new ApprovalInstance(
                    tenantId,
                    ApprovalFlowType.LEAVE,
                    defResp.id(),
                    new SubjectRef("core.leave_request", UUID.randomUUID()),
                    requesterId);

            instance.setStatus(InstanceStatus.APPROVED);
            instance.setCompletedAt(Instant.now());
            instance = instanceRepository.save(instance);

            // Step 0: Delegated
            ApprovalStep step0 =
                    new ApprovalStep(tenantId, instance.getId(), 0, null, ApproverKind.REPORTING_MANAGER, delegateId);
            step0.setDelegatedFromEmployeeId(delegatorId);
            step0.setDecision(ApprovalDecision.APPROVED);
            step0.setComment("Approved by delegate");
            step0.setDecidedAt(Instant.now());
            stepRepository.save(step0);

            // Step 1: Escalated
            ApprovalStep step1 = new ApprovalStep(
                    tenantId, instance.getId(), 1, null, ApproverKind.INDIRECT_MANAGER, escalationAssigneeId);
            step1.setEscalatedFromEmployeeId(escalatedFromId);
            step1.setDecision(ApprovalDecision.APPROVED);
            step1.setComment("Approved by escalated manager");
            step1.setDecidedAt(Instant.now());
            stepRepository.save(step1);

            // Step 2: Reassigned
            ApprovalStep step2 =
                    new ApprovalStep(tenantId, instance.getId(), 2, null, ApproverKind.NAMED_EMPLOYEE, reassignedToId);
            step2.setReassignedFromEmployeeId(reassignedFromId);
            step2.setReassignReason("Original approver left company");
            step2.setDecision(ApprovalDecision.APPROVED);
            step2.setComment("Approved by admin-reassigned employee");
            step2.setDecidedAt(Instant.now());
            stepRepository.save(step2);

            return instance.getId();
        });

        // 1. Without core.approval.read -> 403 Forbidden
        mvc.perform(get("/api/v1/approvals/" + instanceId + "/history")
                        .with(jwt().jwt(b ->
                                b.subject(noPermUserSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        // 2. With core.approval.read -> 200 OK and shows all 3 distinctly
        mvc.perform(get("/api/v1/approvals/" + instanceId + "/history")
                        .with(jwt().jwt(b ->
                                b.subject(readerUserSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value(instanceId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.steps[0].delegatedFromEmployeeId").value(delegatorId.toString()))
                .andExpect(jsonPath("$.steps[0].assigneeEmployeeId").value(delegateId.toString()))
                .andExpect(jsonPath("$.steps[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$.steps[1].escalatedFromEmployeeId").value(escalatedFromId.toString()))
                .andExpect(jsonPath("$.steps[1].assigneeEmployeeId").value(escalationAssigneeId.toString()))
                .andExpect(jsonPath("$.steps[1].decision").value("APPROVED"))
                .andExpect(jsonPath("$.steps[2].reassignedFromEmployeeId").value(reassignedFromId.toString()))
                .andExpect(jsonPath("$.steps[2].reassignReason").value("Original approver left company"))
                .andExpect(jsonPath("$.steps[2].assigneeEmployeeId").value(reassignedToId.toString()))
                .andExpect(jsonPath("$.steps[2].decision").value("APPROVED"));
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
