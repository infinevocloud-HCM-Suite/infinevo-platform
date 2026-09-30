package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * W-15.3, spec section 7 — {@code DelegationIT}.
 *
 * <p>Proves end to end that a stub-flow request raised while the manager is delegated reaches
 * the delegate, and the step records both the delegate and the delegator.
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class DelegationIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = ApprovalTestSchema.TENANT_A;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private DelegationService delegationService;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private ApprovalInstanceRepository instanceRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private org.springframework.transaction.support.TransactionTemplate tx;

    @MockitoBean
    private EmployeeService employeeService;

    private UUID requesterId;
    private UUID managerId;
    private UUID delegateId;

    @BeforeAll
    static void setupSchema() throws Exception {
        ApprovalTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws Exception {
        tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        ApprovalTestSchema.seedTenants();
        ApprovalTestSchema.clearAll();

        TenantContext.set(TENANT_A);

        requesterId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-001", "Requester", "req@infinevo.test");
        managerId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-002", "Manager", "mgr@infinevo.test");
        delegateId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-003", "Delegate", "delegate@infinevo.test");

        ApprovalTestSchema.insertReportingLine(TENANT_A, requesterId, managerId);

        // One-step flow where manager approves
        definitionService.saveDefinition(
                TENANT_A,
                ApprovalFlowType.REGULARIZATION,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "a stub-flow request raised while the manager is delegated reaches the delegate, and the step records both")
    void requestWithActiveDelegationReachesDelegateAndRecordsBoth() {
        // Manager delegates to delegate
        EmployeeResponse delegateResp = mock(EmployeeResponse.class);
        when(delegateResp.id()).thenReturn(delegateId);

        DelegationCreateRequest delegationReq = new DelegationCreateRequest(
                delegateId, LocalDate.now().minusDays(1), LocalDate.now().plusDays(5), null);
        delegationService.createDelegation(TENANT_A, managerId, delegationReq);

        // Start approval instance
        SubjectRef subject = new SubjectRef("core.attendance_regularization", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.REGULARIZATION, subject, requesterId);
        assertThat(instanceId).isNotNull();

        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        assertThat(steps).hasSize(1);

        ApprovalStep step = steps.get(0);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(delegateId);
        assertThat(step.getDelegatedFromEmployeeId()).isEqualTo(managerId);

        // Delegate can decide the step
        when(employeeService.currentEmployee()).thenReturn(Optional.of(delegateResp));

        approvalService.decide(
                step.getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved by delegate"));

        ApprovalInstance completed =
                tx.execute(status -> instanceRepository.findById(instanceId).orElseThrow());
        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.APPROVED);

        ApprovalHistoryResponse history = approvalService.getHistory(instanceId);
        assertThat(history.steps()).hasSize(1);
        ApprovalHistoryResponse.ApprovalHistoryStepResponse stepHistory =
                history.steps().get(0);
        assertThat(stepHistory.assigneeEmployeeId()).isEqualTo(delegateId);
        assertThat(stepHistory.delegatedFromEmployeeId()).isEqualTo(managerId);
        assertThat(stepHistory.decision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(stepHistory.comment()).isEqualTo("Approved by delegate");
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
