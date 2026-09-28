package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.org.ReportingLineService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReassignTest {

    private ApprovalInstanceRepository instanceRepository;
    private ApprovalDefinitionRepository definitionRepository;
    private ApprovalStepRepository stepRepository;
    private EmployeeRepository employeeRepository;
    private ReportingLineService reportingLineService;
    private HolidayQueryService holidayQueryService;
    private EscalationServiceImpl escalationService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID initialAssigneeId = UUID.randomUUID();
    private final UUID targetEmployeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        instanceRepository = mock(ApprovalInstanceRepository.class);
        definitionRepository = mock(ApprovalDefinitionRepository.class);
        stepRepository = mock(ApprovalStepRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        reportingLineService = mock(ReportingLineService.class);
        holidayQueryService = mock(HolidayQueryService.class);

        escalationService = new EscalationServiceImpl(
                instanceRepository,
                definitionRepository,
                stepRepository,
                employeeRepository,
                reportingLineService,
                holidayQueryService);
    }

    @Test
    @DisplayName("a pending step moves to the named employee and records reassigned_from_employee_id")
    void pendingStepMovesToNamedEmployee() {
        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                UUID.randomUUID(),
                new SubjectRef("core.leave_request", UUID.randomUUID()),
                requesterId);
        instance.setStatus(InstanceStatus.PENDING);

        ApprovalStep step =
                new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.REPORTING_MANAGER, initialAssigneeId);

        Employee targetEmployee = mock(Employee.class);
        when(targetEmployee.getId()).thenReturn(targetEmployeeId);
        when(targetEmployee.getTenantId()).thenReturn(tenantId);
        when(targetEmployee.isDeleted()).thenReturn(false);

        when(employeeRepository.findById(targetEmployeeId)).thenReturn(Optional.of(targetEmployee));
        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId))
                .thenReturn(List.of(step));
        when(stepRepository.save(any(ApprovalStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalReassignRequest request = new ApprovalReassignRequest(targetEmployeeId, "Approver on extended leave");
        ApprovalStepResponse response = escalationService.reassign(tenantId, instanceId, request);

        assertThat(step.getAssigneeEmployeeId()).isEqualTo(targetEmployeeId);
        assertThat(step.getReassignedFromEmployeeId()).isEqualTo(initialAssigneeId);
        assertThat(step.getReassignReason()).isEqualTo("Approver on extended leave");
        verify(stepRepository).save(step);
    }

    @Test
    @DisplayName("reassigning a completed instance is refused")
    void completedInstanceReassignRefused() {
        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                UUID.randomUUID(),
                new SubjectRef("core.leave_request", UUID.randomUUID()),
                requesterId);
        instance.setStatus(InstanceStatus.APPROVED);

        Employee targetEmployee = mock(Employee.class);
        when(targetEmployee.getId()).thenReturn(targetEmployeeId);
        when(targetEmployee.getTenantId()).thenReturn(tenantId);
        when(targetEmployee.isDeleted()).thenReturn(false);

        when(employeeRepository.findById(targetEmployeeId)).thenReturn(Optional.of(targetEmployee));
        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));

        ApprovalReassignRequest request = new ApprovalReassignRequest(targetEmployeeId, "Approver left");

        assertThatThrownBy(() -> escalationService.reassign(tenantId, instanceId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot reassign step for non-pending instance");
    }

    @Test
    @DisplayName("a target employee in another tenant is refused")
    void targetInAnotherTenantRefused() {
        UUID otherTenantId = UUID.randomUUID();
        Employee foreignEmployee = mock(Employee.class);
        when(foreignEmployee.getId()).thenReturn(targetEmployeeId);
        when(foreignEmployee.getTenantId()).thenReturn(otherTenantId);
        when(foreignEmployee.isDeleted()).thenReturn(false);

        when(employeeRepository.findById(targetEmployeeId)).thenReturn(Optional.of(foreignEmployee));

        ApprovalReassignRequest request = new ApprovalReassignRequest(targetEmployeeId, "Approver left");

        assertThatThrownBy(() -> escalationService.reassign(tenantId, instanceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target employee not found in tenant");
    }

    @Test
    @DisplayName("reassigning to the requester is refused")
    void targetIsRequesterRefused() {
        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                UUID.randomUUID(),
                new SubjectRef("core.leave_request", UUID.randomUUID()),
                requesterId);
        instance.setStatus(InstanceStatus.PENDING);

        Employee requesterEmp = mock(Employee.class);
        when(requesterEmp.getId()).thenReturn(requesterId);
        when(requesterEmp.getTenantId()).thenReturn(tenantId);
        when(requesterEmp.isDeleted()).thenReturn(false);

        when(employeeRepository.findById(requesterId)).thenReturn(Optional.of(requesterEmp));
        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));

        ApprovalReassignRequest request = new ApprovalReassignRequest(requesterId, "Self reassign");

        assertThatThrownBy(() -> escalationService.reassign(tenantId, instanceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target cannot be the requester");
    }
}
