package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-15.2, spec section 7 — {@code ApprovalOrderingTest}.
 *
 * <p>Proves that sequential refuses an out-of-order decision, and any-order accepts either step first.
 */
class ApprovalOrderingTest {

    private ApprovalDefinitionRepository definitionRepository;
    private ApprovalInstanceRepository instanceRepository;
    private ApprovalStepRepository stepRepository;
    private CoreApproverResolver approverResolver;
    private EmployeeService employeeService;
    private OutcomeDispatcher outcomeDispatcher;
    private ApprovalService approvalService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID approver1 = UUID.randomUUID();
    private final UUID approver2 = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID defSeqId = UUID.randomUUID();
    private final UUID defAnyId = UUID.randomUUID();
    private final UUID step0Id = UUID.randomUUID();
    private final UUID step1Id = UUID.randomUUID();

    private ApprovalStep step0;
    private ApprovalStep step1;
    private ApprovalInstance instance;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);

        definitionRepository = mock(ApprovalDefinitionRepository.class);
        instanceRepository = mock(ApprovalInstanceRepository.class);
        stepRepository = mock(ApprovalStepRepository.class);
        approverResolver = mock(CoreApproverResolver.class);
        employeeService = mock(EmployeeService.class);
        outcomeDispatcher = mock(OutcomeDispatcher.class);

        approvalService = new ApprovalService(
                definitionRepository,
                instanceRepository,
                stepRepository,
                approverResolver,
                employeeService,
                outcomeDispatcher);

        instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                defSeqId,
                new SubjectRef("core.test_subject", UUID.randomUUID()),
                UUID.randomUUID());
        instance.setId(instanceId);

        step0 = new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.REPORTING_MANAGER, approver1);
        step0.setId(step0Id);

        step1 = new ApprovalStep(tenantId, instanceId, 1, null, ApproverKind.ROLE, approver2);
        step1.setId(step1Id);

        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(stepRepository.findByTenantIdAndId(tenantId, step0Id)).thenReturn(Optional.of(step0));
        when(stepRepository.findByTenantIdAndId(tenantId, step1Id)).thenReturn(Optional.of(step1));
        when(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId))
                .thenReturn(List.of(step0, step1));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Sequential refuses deciding step 1 when step 0 is not yet approved")
    void sequentialRefusesOutOfOrderDecision() {
        ApprovalDefinition seqDef = mock(ApprovalDefinition.class);
        when(seqDef.getStepOrdering()).thenReturn(StepOrdering.SEQUENTIAL);
        when(definitionRepository.findById(defSeqId)).thenReturn(Optional.of(seqDef));

        EmployeeResponse emp2 = mock(EmployeeResponse.class);
        when(emp2.id()).thenReturn(approver2);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(emp2));

        assertThatThrownBy(() -> approvalService.decide(
                        step1Id, new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved by 2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sequential ordering requires step 0 to be approved first");

        assertThat(step1.getDecision()).isNull();
    }

    @Test
    @DisplayName("Sequential accepts step 0 first, then step 1")
    void sequentialAcceptsInOrderDecision() {
        ApprovalDefinition seqDef = mock(ApprovalDefinition.class);
        when(seqDef.getStepOrdering()).thenReturn(StepOrdering.SEQUENTIAL);
        when(definitionRepository.findById(defSeqId)).thenReturn(Optional.of(seqDef));

        // Decide step 0
        EmployeeResponse emp1 = mock(EmployeeResponse.class);
        when(emp1.id()).thenReturn(approver1);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(emp1));

        approvalService.decide(step0Id, new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved by 1"));
        assertThat(step0.getDecision()).isEqualTo(ApprovalDecision.APPROVED);

        // Now decide step 1
        EmployeeResponse emp2 = mock(EmployeeResponse.class);
        when(emp2.id()).thenReturn(approver2);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(emp2));

        approvalService.decide(step1Id, new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved by 2"));
        assertThat(step1.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.APPROVED);
    }

    @Test
    @DisplayName("Any-order accepts step 1 first, then step 0")
    void anyOrderAcceptsEitherFirst() {
        instance.setDefinitionId(defAnyId);
        ApprovalDefinition anyDef = mock(ApprovalDefinition.class);
        when(anyDef.getStepOrdering()).thenReturn(StepOrdering.ANY_ORDER);
        when(definitionRepository.findById(defAnyId)).thenReturn(Optional.of(anyDef));

        // Decide step 1 first
        EmployeeResponse emp2 = mock(EmployeeResponse.class);
        when(emp2.id()).thenReturn(approver2);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(emp2));

        approvalService.decide(step1Id, new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved by 2"));
        assertThat(step1.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.PENDING);

        // Decide step 0 next
        EmployeeResponse emp1 = mock(EmployeeResponse.class);
        when(emp1.id()).thenReturn(approver1);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(emp1));

        approvalService.decide(step0Id, new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved by 1"));
        assertThat(step0.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.APPROVED);
    }
}
