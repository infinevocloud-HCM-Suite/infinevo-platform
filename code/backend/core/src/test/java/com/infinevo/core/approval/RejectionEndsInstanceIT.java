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
 * W-15.2, spec section 7 — {@code RejectionEndsInstanceIT}.
 *
 * <p>Proves that a rejection at step 1 completes the instance as rejected, onRejected fires once,
 * and a second start for the same subject creates a new instance rather than reopening.
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class RejectionEndsInstanceIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = ApprovalTestSchema.TENANT_A;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private ApprovalInstanceRepository instanceRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private org.springframework.transaction.support.TransactionTemplate tx;

    @Autowired
    private StubApprovalOutcomeHandler stubHandler;

    @MockitoBean
    private EmployeeService employeeService;

    private UUID requesterId;
    private UUID managerId;

    @BeforeAll
    static void setupSchema() throws Exception {
        ApprovalTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws Exception {
        tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        ApprovalTestSchema.seedTenants();
        ApprovalTestSchema.clearAll();
        stubHandler.reset();

        TenantContext.set(TENANT_A);

        requesterId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-101", "Requester", "req101@infinevo.test");
        managerId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-102", "Manager", "mgr102@infinevo.test");

        ApprovalTestSchema.insertReportingLine(TENANT_A, requesterId, managerId);

        definitionService.saveDefinition(
                TENANT_A,
                ApprovalFlowType.REGULARIZATION,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Rejection completes instance as rejected, fires onRejected once, and resubmission creates a new instance")
    void rejectionCompletesAndResubmissionCreatesNewInstance() {
        SubjectRef subject = new SubjectRef("core.attendance_regularization", UUID.randomUUID());

        // 1. Start first instance
        UUID firstInstanceId = approvalService.start(ApprovalFlowType.REGULARIZATION, subject, requesterId);

        List<ApprovalStep> firstSteps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, firstInstanceId));
        assertThat(firstSteps).hasSize(1);
        ApprovalStep step = firstSteps.get(0);

        // 2. Reject step 0
        EmployeeResponse mgrResponse = mock(EmployeeResponse.class);
        when(mgrResponse.id()).thenReturn(managerId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mgrResponse));

        approvalService.decide(step.getId(), new ApprovalDecideRequest(ApprovalDecision.REJECTED, "Not approved"));

        // 3. First instance is completed with status REJECTED
        ApprovalInstance firstInstance = tx.execute(
                status -> instanceRepository.findById(firstInstanceId).orElseThrow());
        assertThat(firstInstance.getStatus()).isEqualTo(InstanceStatus.REJECTED);
        assertThat(firstInstance.getCompletedAt()).isNotNull();

        assertThat(stubHandler.getRejectedInstances()).containsExactly(firstInstanceId);
        assertThat(stubHandler.getApprovedInstances()).isEmpty();

        // 4. Second start for same subject creates a NEW instance (decision 2)
        UUID secondInstanceId = approvalService.start(ApprovalFlowType.REGULARIZATION, subject, requesterId);
        assertThat(secondInstanceId).isNotEqualTo(firstInstanceId);

        ApprovalInstance secondInstance = tx.execute(
                status -> instanceRepository.findById(secondInstanceId).orElseThrow());
        assertThat(secondInstance.getStatus()).isEqualTo(InstanceStatus.PENDING);
        assertThat(secondInstance.getCompletedAt()).isNull();

        // Verify the original instance is still rejected
        firstInstance = tx.execute(
                status -> instanceRepository.findById(firstInstanceId).orElseThrow());
        assertThat(firstInstance.getStatus()).isEqualTo(InstanceStatus.REJECTED);
    }

    @Test
    @DisplayName("In sequential flow, step 1 is not created at start and does not survive rejection of step 0")
    void sequentialStepDoesNotSurviveRejection() throws Exception {
        UUID hrEmpId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-HR", "HR", "hr@infinevo.test");
        definitionService.saveDefinition(
                TENANT_A,
                ApprovalFlowType.LEAVE,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(
                                new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER),
                                new ApprovalStepDefinition(
                                        ApproverKind.NAMED_EMPLOYEE, hrEmpId.toString(), 3, false))));

        SubjectRef subject = new SubjectRef("core.leave_request", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.LEAVE, subject, requesterId);

        // At start: only step 0 exists
        List<ApprovalStep> stepsAtStart = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        assertThat(stepsAtStart).hasSize(1);
        assertThat(stepsAtStart.get(0).getAssigneeEmployeeId()).isEqualTo(managerId);

        // HR has zero pending steps
        EmployeeResponse hrResponse = mock(EmployeeResponse.class);
        when(hrResponse.id()).thenReturn(hrEmpId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(hrResponse));
        assertThat(approvalService
                        .getPendingSteps(org.springframework.data.domain.PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(0);

        // Manager rejects step 0
        EmployeeResponse mgrResponse = mock(EmployeeResponse.class);
        when(mgrResponse.id()).thenReturn(managerId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mgrResponse));
        approvalService.decide(stepsAtStart.get(0).getId(), new ApprovalDecideRequest(ApprovalDecision.REJECTED, "No"));

        // Step 1 was never created, HR still sees 0 pending steps, step 1 does not survive rejection
        when(employeeService.currentEmployee()).thenReturn(Optional.of(hrResponse));
        assertThat(approvalService
                        .getPendingSteps(org.springframework.data.domain.PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(0);

        List<ApprovalStep> finalSteps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        assertThat(finalSteps).hasSize(1);
        assertThat(finalSteps.get(0).getDecision()).isEqualTo(ApprovalDecision.REJECTED);
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
