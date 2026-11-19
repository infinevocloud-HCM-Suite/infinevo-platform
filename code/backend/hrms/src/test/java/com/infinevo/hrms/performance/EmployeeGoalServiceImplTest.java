package com.infinevo.hrms.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmployeeGoalServiceImplTest {

    private EmployeeGoalRepository goalRepository;
    private EmployeeGoalCheckInRepository checkInRepository;
    private EmployeeRepository employeeRepository;
    private PerformanceCycleRepository cycleRepository;
    private ReportingLineRepository reportingLineRepository;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private EmployeeGoalServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID cycleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        goalRepository = mock(EmployeeGoalRepository.class);
        checkInRepository = mock(EmployeeGoalCheckInRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        cycleRepository = mock(PerformanceCycleRepository.class);
        reportingLineRepository = mock(ReportingLineRepository.class);
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);

        service = new EmployeeGoalServiceImpl(
                goalRepository,
                checkInRepository,
                employeeRepository,
                cycleRepository,
                reportingLineRepository,
                employeeService,
                permissionService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private EmployeeResponse mockEmployee(UUID id, String empNumber, String first, String last) {
        EmployeeResponse resp = mock(EmployeeResponse.class);
        when(resp.id()).thenReturn(id);
        when(resp.employeeNumber()).thenReturn(empNumber);
        when(resp.firstName()).thenReturn(first);
        when(resp.lastName()).thenReturn(last);
        return resp;
    }

    @Test
    @DisplayName("Create goal defaults to IN_PROGRESS with 0 progress")
    void createGoalDefaultsToInProgressAndZeroProgress() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        CreateGoalRequest request = new CreateGoalRequest(
                "Deliver Mobile App OKR",
                "Complete PKCE auth and offline punch sync",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                null,
                false);

        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGoalResponse response = service.createGoal(request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Deliver Mobile App OKR");
        assertThat(response.progressPercentage()).isZero();
        assertThat(response.status()).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(response.employeeId()).isEqualTo(employeeId);
    }

    @Test
    @DisplayName("Create goal with draft=true sets status to DRAFT")
    void createGoalWithDraftTrueSetsDraft() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        CreateGoalRequest request = new CreateGoalRequest(
                "Draft Goal",
                "Work in progress draft",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                null,
                true);

        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGoalResponse response = service.createGoal(request);

        assertThat(response.status()).isEqualTo(GoalStatus.DRAFT);
    }

    @Test
    @DisplayName("Create goal rejects NUMERIC metric with target 0 or negative")
    void createGoalRejectsNumericWithTargetZeroOrNegative() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        CreateGoalRequest zeroReq = new CreateGoalRequest(
                "Zero Target Goal",
                "Desc",
                GoalCategory.INDIVIDUAL,
                MetricType.NUMERIC,
                BigDecimal.ZERO,
                "units",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                null,
                false);

        assertThatThrownBy(() -> service.createGoal(zeroReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetValue must be greater than zero for NUMERIC metric type");
    }

    @Test
    @DisplayName("Create goal linked to a CLOSED cycle is rejected with 409 (IllegalStateException)")
    void createGoalLinkedToClosedCycleThrowsConflict() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        PerformanceCycle closedCycle = mock(PerformanceCycle.class);
        when(closedCycle.getStatus()).thenReturn(PerformanceCycleStatus.CLOSED);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), eq(tenantId))).thenReturn(Optional.of(closedCycle));

        CreateGoalRequest request = new CreateGoalRequest(
                "Linked Goal",
                "Desc",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                cycleId,
                false);

        assertThatThrownBy(() -> service.createGoal(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot link goal to a closed performance cycle");
    }

    @Test
    @DisplayName("Start transitions DRAFT to IN_PROGRESS")
    void startGoalTransitionsDraftToInProgress() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        EmployeeGoal draftGoal = new EmployeeGoal(
                tenantId,
                employeeId,
                null,
                "Draft Goal",
                "Desc",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                true,
                "alex");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(draftGoal));
        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGoalResponse response = service.startGoal(goalId);

        assertThat(response.status()).isEqualTo(GoalStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Cancel transitions goal to CANCELLED")
    void cancelGoalTransitionsToCancelled() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        EmployeeGoal goal = new EmployeeGoal(
                tenantId,
                employeeId,
                null,
                "In Progress Goal",
                "Desc",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                false,
                "alex");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGoalResponse response = service.cancelGoal(goalId);

        assertThat(response.status()).isEqualTo(GoalStatus.CANCELLED);
    }

    @Test
    @DisplayName("Check-in saves note to checkInRepository and calculates NUMERIC progress")
    void checkInSavesNoteAndCalculatesProgress() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        EmployeeGoal goal = new EmployeeGoal(
                tenantId,
                employeeId,
                null,
                "Ship OKR",
                "Target 50 customer reviews",
                GoalCategory.INDIVIDUAL,
                MetricType.NUMERIC,
                new BigDecimal("50.00"),
                "reviews",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                false,
                "alex");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalCheckInRequest checkInReq = new GoalCheckInRequest(new BigDecimal("25.00"), "First 25 collected");
        EmployeeGoalResponse response = service.checkIn(goalId, checkInReq);

        assertThat(response.progressPercentage()).isEqualTo(50);
        assertThat(response.status()).isEqualTo(GoalStatus.IN_PROGRESS);

        verify(checkInRepository).save(any(EmployeeGoalCheckIn.class));
    }

    @Test
    @DisplayName("Check-in reaching 100% auto-transitions to ACHIEVED, and a goal at 0% cannot become ACHIEVED")
    void checkInReaching100AutoTransitionsToAchieved() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        EmployeeGoal goal = new EmployeeGoal(
                tenantId,
                employeeId,
                null,
                "Ship OKR",
                "Target 50 customer reviews",
                GoalCategory.INDIVIDUAL,
                MetricType.NUMERIC,
                new BigDecimal("50.00"),
                "reviews",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                false,
                "alex");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        // Check-in at 0: progress is 0, status stays IN_PROGRESS
        service.checkIn(goalId, new GoalCheckInRequest(BigDecimal.ZERO, "Just starting"));
        assertThat(goal.getProgressPercentage()).isZero();
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.IN_PROGRESS);

        // Check-in reaching 50: progress is 100%, status becomes ACHIEVED
        EmployeeGoalResponse response =
                service.checkIn(goalId, new GoalCheckInRequest(new BigDecimal("50.00"), "Completed all 50!"));
        assertThat(response.progressPercentage()).isEqualTo(100);
        assertThat(response.status()).isEqualTo(GoalStatus.ACHIEVED);
    }

    @Test
    @DisplayName("ACHIEVED and CANCELLED refuse every change with 409 (IllegalStateException)")
    void finalStatesRefuseEveryChange() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        EmployeeGoal goal = new EmployeeGoal(
                tenantId,
                employeeId,
                null,
                "Achieved Goal",
                "Desc",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                false,
                "alex");
        // Bring to achieved
        goal.checkIn(new BigDecimal("100.00"), "alex");
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACHIEVED);

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goal));

        // Check-in refused
        assertThatThrownBy(() -> service.checkIn(goalId, new GoalCheckInRequest(new BigDecimal("100.00"), "Notes")))
                .isInstanceOf(IllegalStateException.class);

        // Edit refused
        UpdateGoalRequest editReq = new UpdateGoalRequest("New Title", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.editGoal(goalId, editReq)).isInstanceOf(IllegalStateException.class);

        // Cancel refused
        assertThatThrownBy(() -> service.cancelGoal(goalId)).isInstanceOf(IllegalStateException.class);

        // Start refused
        assertThatThrownBy(() -> service.startGoal(goalId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Goal on a CLOSED cycle refuses every change with 409 (IllegalStateException)")
    void goalOnClosedCycleRefusesChanges() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        EmployeeGoal goal = new EmployeeGoal(
                tenantId,
                employeeId,
                cycleId,
                "Cycle Goal",
                "Desc",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                false,
                "alex");

        PerformanceCycle closedCycle = mock(PerformanceCycle.class);
        when(closedCycle.getStatus()).thenReturn(PerformanceCycleStatus.CLOSED);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), eq(tenantId))).thenReturn(Optional.of(closedCycle));

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> service.checkIn(goalId, new GoalCheckInRequest(new BigDecimal("50.00"), "note")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed performance cycle");

        assertThatThrownBy(() -> service.cancelGoal(goalId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed performance cycle");
    }

    @Test
    @DisplayName("MILESTONE metric type works as 0 or 100")
    void milestoneMetricProgressCalculation() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        EmployeeGoal goal = new EmployeeGoal(
                tenantId,
                employeeId,
                null,
                "Milestone Goal",
                "Desc",
                GoalCategory.INDIVIDUAL,
                MetricType.MILESTONE,
                BigDecimal.ONE,
                "binary",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                false,
                "alex");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.checkIn(goalId, new GoalCheckInRequest(BigDecimal.ZERO, "Not done"));
        assertThat(goal.getProgressPercentage()).isZero();
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.IN_PROGRESS);

        service.checkIn(goalId, new GoalCheckInRequest(BigDecimal.ONE, "Done!"));
        assertThat(goal.getProgressPercentage()).isEqualTo(100);
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACHIEVED);
    }

    @Test
    @DisplayName("Get team goals returns goals of manager direct reports")
    void getTeamGoalsResolvesDirectReports() {
        EmployeeResponse mgrResp = mockEmployee(managerId, "MGR-001", "Sarah", "Connor");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mgrResp));

        Employee directReport = mock(Employee.class);
        when(directReport.getId()).thenReturn(employeeId);
        when(directReport.getFirstName()).thenReturn("Alex");
        when(directReport.getLastName()).thenReturn("Vance");
        when(directReport.getEmployeeNumber()).thenReturn("EMP-001");

        ReportingLine reportingLine = mock(ReportingLine.class);
        when(reportingLine.getEmployee()).thenReturn(directReport);

        when(reportingLineRepository.findDirectReports(eq(tenantId), eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(reportingLine));

        EmployeeGoal goal = new EmployeeGoal(
                tenantId,
                employeeId,
                null,
                "Direct report goal",
                "Description",
                GoalCategory.TEAM,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                false,
                "alex");

        when(goalRepository.findByTenantIdAndEmployeeIdInOrderByCreatedAtDesc(eq(tenantId), eq(List.of(employeeId))))
                .thenReturn(List.of(goal));
        when(employeeRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(directReport));

        List<EmployeeGoalResponse> teamGoals = service.getTeamGoals();

        assertThat(teamGoals).hasSize(1);
        assertThat(teamGoals.get(0).title()).isEqualTo("Direct report goal");
        assertThat(teamGoals.get(0).employeeName()).isEqualTo("Alex Vance");
    }
}
