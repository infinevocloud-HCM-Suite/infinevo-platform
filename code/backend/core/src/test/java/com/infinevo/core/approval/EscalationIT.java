package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
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
 * W-15.3, spec section 7 — {@code EscalationIT}.
 *
 * <p>Proves that an overdue step reassigns up the reporting line and the history records the escalation.
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class EscalationIT extends com.infinevo.shared.test.AbstractIntegrationTest {

    private static final UUID TENANT_A = ApprovalTestSchema.TENANT_A;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private EscalationService escalationService;

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
    private UUID directorId;

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
        directorId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-003", "Director", "director@infinevo.test");

        ApprovalTestSchema.insertReportingLine(TENANT_A, requesterId, managerId);
        ApprovalTestSchema.insertReportingLine(TENANT_A, managerId, directorId);

        definitionService.saveDefinition(
                TENANT_A,
                ApprovalFlowType.LEAVE,
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
    @DisplayName("an overdue step reassigns and the history shows the escalation")
    void overdueStepEscalatesAndHistoryShowsEscalation() throws Exception {
        SubjectRef subject = new SubjectRef("core.leave_request", UUID.randomUUID());
        UUID instanceId = approvalService.start(ApprovalFlowType.LEAVE, subject, requesterId);

        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        assertThat(steps).hasSize(1);
        ApprovalStep step = steps.get(0);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(managerId);

        // Backdate step creation to 10 days ago (definitely overdue) via raw SQL so JPA lifecycle callbacks don't
        // overwrite it
        try (var conn = ApprovalTestSchema.migrationConnection();
                var ps = conn.prepareStatement(
                        "UPDATE core.approval_step SET created_at = NOW() - INTERVAL '10 days', updated_at = NOW() - INTERVAL '10 days' WHERE id = ?")) {
            ps.setObject(1, step.getId());
            ps.executeUpdate();
        }

        // Trigger escalation
        int escalated = escalationService.escalateOverdueSteps(TENANT_A, LocalDate.now());
        assertThat(escalated).isEqualTo(1);

        // Verify step reassigned to director
        ApprovalStep escalatedStep =
                tx.execute(status -> stepRepository.findById(step.getId()).orElseThrow());
        assertThat(escalatedStep.getAssigneeEmployeeId()).isEqualTo(directorId);
        assertThat(escalatedStep.getEscalatedFromEmployeeId()).isEqualTo(managerId);

        // History shows escalation
        ApprovalHistoryResponse history = approvalService.getHistory(instanceId);
        ApprovalHistoryResponse.ApprovalHistoryStepResponse historyStep =
                history.steps().get(0);
        assertThat(historyStep.assigneeEmployeeId()).isEqualTo(directorId);
        assertThat(historyStep.escalatedFromEmployeeId()).isEqualTo(managerId);

        // Director can decide the step
        EmployeeResponse directorResp = mock(EmployeeResponse.class);
        when(directorResp.id()).thenReturn(directorId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(directorResp));

        approvalService.decide(
                step.getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved by director"));

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
