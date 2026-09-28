package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.org.ReportingLineService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EscalationServiceTest {

    private ApprovalInstanceRepository instanceRepository;
    private ApprovalDefinitionRepository definitionRepository;
    private ApprovalStepRepository stepRepository;
    private EmployeeRepository employeeRepository;
    private ReportingLineService reportingLineService;
    private HolidayQueryService holidayQueryService;
    private EscalationServiceImpl escalationService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID definitionId = UUID.randomUUID();
    private final UUID approverId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();

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
    @DisplayName("a step assigned on Friday with escalate_after_days = 3 is not overdue on Monday")
    void stepUnderThresholdIsUntouched() {
        // Friday 2026-09-18
        LocalDate friday = LocalDate.of(2026, 9, 18);
        Instant fridayInstant = friday.atStartOfDay(ZoneOffset.UTC).toInstant();
        LocalDate monday = LocalDate.of(2026, 9, 21);

        ApprovalStep step = new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.REPORTING_MANAGER, approverId);
        step.setCreatedAt(fridayInstant);
        step.setUpdatedAt(fridayInstant);

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                definitionId,
                new SubjectRef("core.leave_request", UUID.randomUUID()),
                UUID.randomUUID());
        instance.setStatus(InstanceStatus.PENDING);

        ApprovalDefinition definition = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.LEAVE,
                StepOrdering.SEQUENTIAL,
                List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false)),
                CommentScope.PER_STEP,
                true,
                LocalDate.of(2024, 1, 1),
                "system");

        when(stepRepository.findByTenantIdAndDecisionIsNullOrderByCreatedAtAsc(tenantId))
                .thenReturn(List.of(step));
        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));

        int escalated = escalationService.escalateOverdueSteps(tenantId, monday);

        assertThat(escalated).isEqualTo(0);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(approverId);
        assertThat(step.getEscalatedFromEmployeeId()).isNull();
        verify(stepRepository, never()).save(any());
    }

    @Test
    @DisplayName("a step over threshold moves one level up")
    void stepOverThresholdMovesOneLevel() {
        // Friday 2026-09-18
        LocalDate friday = LocalDate.of(2026, 9, 18);
        Instant fridayInstant = friday.atStartOfDay(ZoneOffset.UTC).toInstant();
        // Thursday 2026-09-24 (4 working days elapsed: Mon, Tue, Wed, Thu)
        LocalDate thursday = LocalDate.of(2026, 9, 24);

        ApprovalStep step = new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.REPORTING_MANAGER, approverId);
        step.setCreatedAt(fridayInstant);
        step.setUpdatedAt(fridayInstant);

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                definitionId,
                new SubjectRef("core.leave_request", UUID.randomUUID()),
                UUID.randomUUID());
        instance.setStatus(InstanceStatus.PENDING);

        ApprovalDefinition definition = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.LEAVE,
                StepOrdering.SEQUENTIAL,
                List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false)),
                CommentScope.PER_STEP,
                true,
                LocalDate.of(2024, 1, 1),
                "system");

        Employee manager = mock(Employee.class);
        when(manager.getId()).thenReturn(managerId);

        when(stepRepository.findByTenantIdAndDecisionIsNullOrderByCreatedAtAsc(tenantId))
                .thenReturn(List.of(step));
        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));
        when(reportingLineService.chainAbove(approverId, thursday)).thenReturn(List.of(manager));

        int escalated = escalationService.escalateOverdueSteps(tenantId, thursday);

        assertThat(escalated).isEqualTo(1);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(managerId);
        assertThat(step.getEscalatedFromEmployeeId()).isEqualTo(approverId);
        verify(stepRepository).save(step);
    }

    @Test
    @DisplayName("an approver with no manager above yields unassignable rather than looping")
    void approverWithNoManagerAboveYieldsUnassignable() {
        LocalDate friday = LocalDate.of(2026, 9, 18);
        Instant fridayInstant = friday.atStartOfDay(ZoneOffset.UTC).toInstant();
        LocalDate thursday = LocalDate.of(2026, 9, 24);

        ApprovalStep step = new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.REPORTING_MANAGER, approverId);
        step.setCreatedAt(fridayInstant);
        step.setUpdatedAt(fridayInstant);

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                definitionId,
                new SubjectRef("core.leave_request", UUID.randomUUID()),
                UUID.randomUUID());
        instance.setStatus(InstanceStatus.PENDING);

        ApprovalDefinition definition = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.LEAVE,
                StepOrdering.SEQUENTIAL,
                List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false)),
                CommentScope.PER_STEP,
                true,
                LocalDate.of(2024, 1, 1),
                "system");

        when(stepRepository.findByTenantIdAndDecisionIsNullOrderByCreatedAtAsc(tenantId))
                .thenReturn(List.of(step));
        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));
        // No manager in chain
        when(reportingLineService.chainAbove(approverId, thursday)).thenReturn(Collections.emptyList());

        int escalated = escalationService.escalateOverdueSteps(tenantId, thursday);

        assertThat(escalated).isEqualTo(1);
        assertThat(step.getAssigneeEmployeeId()).isNull(); // unassignable
        assertThat(step.getEscalatedFromEmployeeId()).isEqualTo(approverId);
        verify(stepRepository).save(step);
    }

    @Test
    @DisplayName("a holiday from holidaysBetween extends the escalation clock by a day")
    void holidayExtendsEscalationClock() {
        LocalDate friday = LocalDate.of(2026, 9, 18);
        Instant fridayInstant = friday.atStartOfDay(ZoneOffset.UTC).toInstant();
        LocalDate thursday = LocalDate.of(2026, 9, 24);
        LocalDate fridayNext = LocalDate.of(2026, 9, 25);
        LocalDate mondayHoliday = LocalDate.of(2026, 9, 21);

        ApprovalStep step = new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.REPORTING_MANAGER, approverId);
        step.setCreatedAt(fridayInstant);
        step.setUpdatedAt(fridayInstant);

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                definitionId,
                new SubjectRef("core.leave_request", UUID.randomUUID()),
                UUID.randomUUID());
        instance.setStatus(InstanceStatus.PENDING);

        ApprovalDefinition definition = new ApprovalDefinition(
                tenantId,
                ApprovalFlowType.LEAVE,
                StepOrdering.SEQUENTIAL,
                List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER, null, 3, false)),
                CommentScope.PER_STEP,
                true,
                LocalDate.of(2024, 1, 1),
                "system");

        Employee manager = mock(Employee.class);
        when(manager.getId()).thenReturn(managerId);

        when(stepRepository.findByTenantIdAndDecisionIsNullOrderByCreatedAtAsc(tenantId))
                .thenReturn(List.of(step));
        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));
        // Return Monday holiday
        when(holidayQueryService.holidaysBetween(any(), eq(friday), eq(thursday)))
                .thenReturn(List.of(mondayHoliday));
        when(holidayQueryService.holidaysBetween(any(), eq(friday), eq(fridayNext)))
                .thenReturn(List.of(mondayHoliday));
        when(reportingLineService.chainAbove(approverId, fridayNext)).thenReturn(List.of(manager));

        // On Thursday: with Monday holiday, only 3 working days passed (Tue, Wed, Thu) -> untouched!
        int escalatedThursday = escalationService.escalateOverdueSteps(tenantId, thursday);
        assertThat(escalatedThursday).isEqualTo(0);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(approverId);

        // On FridayNext: 4 working days passed (Tue, Wed, Thu, Fri) -> escalated!
        int escalatedFriday = escalationService.escalateOverdueSteps(tenantId, fridayNext);
        assertThat(escalatedFriday).isEqualTo(1);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(managerId);
        assertThat(step.getEscalatedFromEmployeeId()).isEqualTo(approverId);
    }
}
