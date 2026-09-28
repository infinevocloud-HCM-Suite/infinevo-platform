package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-15.2, spec section 7 — {@code ApprovedAmountTest}.
 *
 * <p>Proves that a decision with approvedAmount stores it as Money and passes it to the handler,
 * a decision without one stores null, and a negative amount is refused.
 */
class ApprovedAmountTest {

    private ApprovalDefinitionRepository definitionRepository;
    private ApprovalInstanceRepository instanceRepository;
    private ApprovalStepRepository stepRepository;
    private CoreApproverResolver approverResolver;
    private EmployeeService employeeService;
    private OutcomeDispatcher outcomeDispatcher;
    private ApprovalService approvalService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID approverId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID defId = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();

    private ApprovalStep step;
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
                ApprovalFlowType.REIMBURSEMENT,
                defId,
                new SubjectRef("core.reimbursement", UUID.randomUUID()),
                UUID.randomUUID());
        instance.setId(instanceId);

        step = new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.ROLE, approverId);
        step.setId(stepId);

        ApprovalDefinition def = mock(ApprovalDefinition.class);
        when(def.getStepOrdering()).thenReturn(StepOrdering.SEQUENTIAL);
        when(definitionRepository.findById(defId)).thenReturn(Optional.of(def));

        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(stepRepository.findByTenantIdAndId(tenantId, stepId)).thenReturn(Optional.of(step));
        when(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId))
                .thenReturn(List.of(step));

        EmployeeResponse emp = mock(EmployeeResponse.class);
        when(emp.id()).thenReturn(approverId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(emp));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Decision with approvedAmount stores it as Money and records on step")
    void decisionWithApprovedAmountStoresMoney() {
        BigDecimal amount = new BigDecimal("1250.50");
        ApprovalDecideRequest request = new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved amount", amount);

        approvalService.decide(stepId, request);

        assertThat(step.getApprovedAmount()).isEqualTo(amount);
        assertThat(step.getApprovedMoney()).isEqualTo(Money.of(amount));
        assertThat(step.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        verify(outcomeDispatcher).dispatchAfterCommit(instanceId);
    }

    @Test
    @DisplayName("Decision without approvedAmount stores null")
    void decisionWithoutApprovedAmountStoresNull() {
        ApprovalDecideRequest request = new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved full claim");

        approvalService.decide(stepId, request);

        assertThat(step.getApprovedAmount()).isNull();
        assertThat(step.getApprovedMoney()).isNull();
    }

    @Test
    @DisplayName("Negative approvedAmount is refused with IllegalArgumentException")
    void negativeApprovedAmountRefused() {
        BigDecimal negative = new BigDecimal("-50.00");

        assertThatThrownBy(() -> new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Negative", negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approvedAmount must not be negative");
    }
}
