package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * W-15.2, spec section 7 — {@code StubFlowApprovalIT}.
 *
 * <p>Proves end to end that a REGULARIZATION instance routes along the reporting line to the manager,
 * then to a second step, and on final approval {@code StubApprovalOutcomeHandler.onApproved} is called
 * exactly once after the completing transaction committed.
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class StubFlowApprovalIT extends AbstractIntegrationTest {

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

    @Autowired
    private EscalationSweep escalationSweep;

    @MockitoBean
    private EmployeeService employeeService;

    private UUID requesterId;
    private UUID managerId;
    private UUID adminEmpId;

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

        requesterId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-001", "Requester", "req@infinevo.test");
        managerId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-002", "Manager", "mgr@infinevo.test");
        adminEmpId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-003", "AdminEmp", "admin@infinevo.test");

        ApprovalTestSchema.insertReportingLine(TENANT_A, requesterId, managerId);

        // Configure a two-step sequential flow for REGULARIZATION
        definitionService.saveDefinition(
                TENANT_A,
                ApprovalFlowType.REGULARIZATION,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(
                                new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER),
                                new ApprovalStepDefinition(
                                        ApproverKind.NAMED_EMPLOYEE, adminEmpId.toString(), 3, false))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("REGULARIZATION routes to manager then second step; handler runs once after commit")
    void regularizationRoutesAndCompletesAfterCommit() throws Exception {
        AtomicBoolean sawApprovedInSecondConnection = new AtomicBoolean(false);

        stubHandler.setOnApprovedHook(instanceId -> {
            try (Connection conn = ApprovalTestSchema.migrationConnection();
                    PreparedStatement ps =
                            conn.prepareStatement("SELECT status FROM core.approval_instance WHERE id = ?")) {
                ps.setObject(1, instanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String status = rs.getString("status");
                        if ("APPROVED".equals(status)) {
                            sawApprovedInSecondConnection.set(true);
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to check status in separate connection", e);
            }
        });

        // 1. Start approval instance
        SubjectRef subject = new SubjectRef("core.attendance_regularization", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.REGULARIZATION, subject, requesterId);
        assertThat(instanceId).isNotNull();

        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        assertThat(steps).hasSize(1);

        ApprovalStep step0 = steps.get(0);
        assertThat(step0.getStepIndex()).isEqualTo(0);
        assertThat(step0.getApproverKind()).isEqualTo(ApproverKind.REPORTING_MANAGER);
        assertThat(step0.getAssigneeEmployeeId()).isEqualTo(managerId);

        // 2. Manager approves step 0
        EmployeeResponse mgrResponse = mock(EmployeeResponse.class);
        when(mgrResponse.id()).thenReturn(managerId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mgrResponse));

        approvalService.decide(
                step0.getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Manager looks good"));

        ApprovalInstance afterStep0 =
                tx.execute(status -> instanceRepository.findById(instanceId).orElseThrow());
        assertThat(afterStep0.getStatus()).isEqualTo(InstanceStatus.PENDING);
        assertThat(stubHandler.getApprovedInstances()).isEmpty();

        // Sequential activation: step 1 is activated upon step 0 approval
        List<ApprovalStep> stepsAfterStep0 = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        assertThat(stepsAfterStep0).hasSize(2);

        ApprovalStep step1 = stepsAfterStep0.get(1);
        assertThat(step1.getStepIndex()).isEqualTo(1);
        assertThat(step1.getApproverKind()).isEqualTo(ApproverKind.NAMED_EMPLOYEE);
        assertThat(step1.getAssigneeEmployeeId()).isEqualTo(adminEmpId);

        // 3. Admin approves step 1 (final step)
        EmployeeResponse adminResponse = mock(EmployeeResponse.class);
        when(adminResponse.id()).thenReturn(adminEmpId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(adminResponse));

        approvalService.decide(
                step1.getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Admin final sign-off"));

        ApprovalInstance completed =
                tx.execute(status -> instanceRepository.findById(instanceId).orElseThrow());
        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.APPROVED);
        assertThat(completed.getCompletedAt()).isNotNull();

        // 4. Assert handler was called exactly once, after commit
        assertThat(stubHandler.getApprovedInstances()).containsExactly(instanceId);
        assertThat(sawApprovedInSecondConnection.get()).isTrue();

        List<StepDecision> decisions = stubHandler.getApprovedDecisions(instanceId);
        assertThat(decisions).hasSize(2);
        assertThat(decisions.get(0).decision()).isEqualTo("APPROVED");
        assertThat(decisions.get(0).comment()).isEqualTo("Manager looks good");
        assertThat(decisions.get(1).decision()).isEqualTo("APPROVED");
        assertThat(decisions.get(1).comment()).isEqualTo("Admin final sign-off");
    }

    @Test
    @DisplayName("Failed outcome dispatch is retried when the background sweep job runs")
    void failedOutcomeDispatchIsRetriedBySweepJob() throws Exception {
        // 1. Configure the handler to simulate downstream failure (e.g. payroll temporarily unreachable)
        AtomicBoolean downstreamFails = new AtomicBoolean(true);
        stubHandler.setBeforeApprovedHook(() -> {
            if (downstreamFails.get()) {
                throw new RuntimeException("Downstream payroll service unavailable");
            }
        });

        // 2. Start approval instance
        SubjectRef subject = new SubjectRef("core.attendance_regularization", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.REGULARIZATION, subject, requesterId);

        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        ApprovalStep step0 = steps.get(0);

        EmployeeResponse mgrResponse = mock(EmployeeResponse.class);
        when(mgrResponse.id()).thenReturn(managerId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mgrResponse));

        // Manager approves step 0
        approvalService.decide(step0.getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Manager approved"));

        // Admin approves step 1 (final step)
        List<ApprovalStep> stepsAfterStep0 = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        ApprovalStep step1 = stepsAfterStep0.get(1);

        EmployeeResponse adminResponse = mock(EmployeeResponse.class);
        when(adminResponse.id()).thenReturn(adminEmpId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(adminResponse));

        // When deciding step 1, instance is marked APPROVED, but downstream notification throws
        approvalService.decide(step1.getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Admin approved"));

        // Verify instance is APPROVED, but outcomeNotifiedAt is null and handler has not recorded success
        ApprovalInstance completed =
                tx.execute(status -> instanceRepository.findById(instanceId).orElseThrow());
        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.APPROVED);
        assertThat(completed.getOutcomeNotifiedAt()).isNull();
        assertThat(stubHandler.getApprovedInstances()).isEmpty();

        // 3. Downstream service recovers
        downstreamFails.set(false);

        // 4. Run the scheduled sweep job (simulating the background run)
        TenantContext.clear();
        escalationSweep.sweep();

        // 5. Verify the sweep job successfully retried the outcome notification and stamped outcomeNotifiedAt
        assertThat(stubHandler.getApprovedInstances()).containsExactly(instanceId);

        TenantContext.set(TENANT_A);
        ApprovalInstance retriedInstance =
                tx.execute(status -> instanceRepository.findById(instanceId).orElseThrow());
        assertThat(retriedInstance.getOutcomeNotifiedAt()).isNotNull();
    }
}
