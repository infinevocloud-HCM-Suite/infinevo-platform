package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
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
 * W-15.2, spec section 7 — {@code ApprovalRlsIT}.
 *
 * <p>Proves tenant isolation on approval instances and steps under row-level security:
 * Tenant A cannot see, query or decide Tenant B's approval steps.
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class ApprovalRlsIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = ApprovalTestSchema.TENANT_A;
    private static final UUID TENANT_B = ApprovalTestSchema.TENANT_B;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private org.springframework.transaction.support.TransactionTemplate tx;

    @MockitoBean
    private EmployeeService employeeService;

    private UUID empAId;
    private UUID empBId;
    private UUID instanceBId;
    private UUID stepBId;

    @BeforeAll
    static void setupSchema() throws Exception {
        ApprovalTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws Exception {
        tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        ApprovalTestSchema.seedTenants();
        ApprovalTestSchema.clearAll();

        // 1. Setup Tenant A
        TenantContext.set(TENANT_A);
        empAId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-A", "Alice", "alice@a.test");
        definitionService.saveDefinition(
                TENANT_A,
                ApprovalFlowType.REGULARIZATION,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.NAMED_EMPLOYEE, empAId.toString(), 3, false))));

        // 2. Setup Tenant B
        TenantContext.set(TENANT_B);
        empBId = ApprovalTestSchema.insertEmployee(TENANT_B, "EMP-B", "Bob", "bob@b.test");
        definitionService.saveDefinition(
                TENANT_B,
                ApprovalFlowType.REGULARIZATION,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.NAMED_EMPLOYEE, empBId.toString(), 3, false))));

        // Start instance in Tenant B
        SubjectRef subjectB = new SubjectRef("core.attendance_regularization", UUID.randomUUID());
        instanceBId = approvalService.start(ApprovalFlowType.REGULARIZATION, subjectB, empBId);

        List<ApprovalStep> stepsB = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_B, instanceBId));
        assertThat(stepsB).hasSize(1);
        stepBId = stepsB.get(0).getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A cannot see or decide Tenant B's approval steps")
    void tenantIsolationOnApprovals() throws Exception {
        // Switch to Tenant A
        TenantContext.set(TENANT_A);

        EmployeeResponse empAResponse = mock(EmployeeResponse.class);
        when(empAResponse.id()).thenReturn(empAId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empAResponse));

        // 1. Attempting to fetch Tenant B's instance throws NoSuchElementException
        assertThatThrownBy(() -> approvalService.getInstance(instanceBId)).isInstanceOf(NoSuchElementException.class);

        // 2. Attempting to decide Tenant B's step throws NoSuchElementException
        assertThatThrownBy(() -> approvalService.decide(
                        stepBId, new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Attempt from Tenant A")))
                .isInstanceOf(NoSuchElementException.class);

        // 3. Raw connection under app_user RLS proves zero visible steps in Tenant A
        assertThat(ApprovalTestSchema.visibleStepCount(TENANT_A)).isEqualTo(0);
        assertThat(ApprovalTestSchema.visibleStepCount(TENANT_B)).isEqualTo(1);
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
