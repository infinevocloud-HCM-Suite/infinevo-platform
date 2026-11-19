package com.infinevo.hrms.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private EmployeeRepository employeeRepository;
    private PerformanceCycleRepository cycleRepository;
    private ReportingLineRepository reportingLineRepository;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private EmployeeGoalServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        goalRepository = mock(EmployeeGoalRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        cycleRepository = mock(PerformanceCycleRepository.class);
        reportingLineRepository = mock(ReportingLineRepository.class);
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);

        service = new EmployeeGoalServiceImpl(
                goalRepository,
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
    @DisplayName("Create goal validates dates, calculates initial progress, and saves")
    void createGoalValidatesAndSaves() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        CreateGoalRequest request = new CreateGoalRequest(
                "Deliver Mobile App OKR",
                "Complete PKCE auth and offline punch sync",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                "%",
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                null,
                GoalStatus.IN_PROGRESS);

        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGoalResponse response = service.createGoal(request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Deliver Mobile App OKR");
        assertThat(response.progressPercentage()).isEqualTo(40);
        assertThat(response.status()).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(response.employeeId()).isEqualTo(employeeId);
    }

    @Test
    @DisplayName("Create goal rejects due date before start date")
    void createGoalRejectsInvalidDates() {
        EmployeeResponse empResp = mockEmployee(employeeId, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        CreateGoalRequest request = new CreateGoalRequest(
                "Invalid Dates Goal",
                "Description",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "%",
                0,
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 1, 1),
                null,
                GoalStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.createGoal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Due date must not be before start date");
    }

    @Test
    @DisplayName("Update goal progress recalculates % and auto-transitions to ACHIEVED at 100%")
    void updateProgressCalculatesAndTransitionsToAchieved() {
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
                new BigDecimal("25.00"),
                "reviews",
                50,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                GoalStatus.IN_PROGRESS,
                "alex");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(EmployeeGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateGoalProgressRequest updateReq =
                new UpdateGoalProgressRequest(new BigDecimal("50.00"), null, null, "All reviews collected!");

        EmployeeGoalResponse response = service.updateProgress(goalId, updateReq);

        assertThat(response).isNotNull();
        assertThat(response.progressPercentage()).isEqualTo(100);
        assertThat(response.status()).isEqualTo(GoalStatus.ACHIEVED);
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
                new BigDecimal("75.00"),
                "%",
                75,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                GoalStatus.IN_PROGRESS,
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
