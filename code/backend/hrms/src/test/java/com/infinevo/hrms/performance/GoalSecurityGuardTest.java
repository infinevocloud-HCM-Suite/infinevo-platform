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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class GoalSecurityGuardTest {

    private EmployeeGoalRepository goalRepository;
    private EmployeeGoalCheckInRepository checkInRepository;
    private EmployeeRepository employeeRepository;
    private PerformanceCycleRepository cycleRepository;
    private ReportingLineRepository reportingLineRepository;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private EmployeeGoalServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeA = UUID.randomUUID();
    private final UUID employeeB = UUID.randomUUID();

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
        EmployeeResponse resp = mockEmployee(id, empNumber);
        when(resp.firstName()).thenReturn(first);
        when(resp.lastName()).thenReturn(last);
        return resp;
    }

    private EmployeeResponse mockEmployee(UUID id, String empNumber) {
        EmployeeResponse resp = mock(EmployeeResponse.class);
        when(resp.id()).thenReturn(id);
        when(resp.employeeNumber()).thenReturn(empNumber);
        return resp;
    }

    @Test
    @DisplayName("User without linked employee profile is rejected with AccessDeniedException")
    void unlinkedUserThrowsAccessDenied() {
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyGoals())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No employee profile linked to current user account");
    }

    @Test
    @DisplayName("Manager cannot write to a direct report's goal (writes are owner-only)")
    void managerCannotWriteToReportGoal() {
        EmployeeResponse mgrResp = mockEmployee(employeeA, "MGR-001", "Sarah", "Connor");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mgrResp));

        EmployeeGoal goalB = new EmployeeGoal(
                tenantId,
                employeeB,
                null,
                "Goal of B",
                "Description",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                false,
                "b");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goalB));

        // Employee A is manager of Employee B
        Employee report = mock(Employee.class);
        when(report.getId()).thenReturn(employeeB);
        ReportingLine line = mock(ReportingLine.class);
        when(line.getEmployee()).thenReturn(report);
        when(reportingLineRepository.findDirectReports(eq(tenantId), eq(employeeA), any(LocalDate.class)))
                .thenReturn(List.of(line));

        // Check-in refused
        GoalCheckInRequest checkInReq = new GoalCheckInRequest(new BigDecimal("50.00"), "Manager trying check-in");
        assertThatThrownBy(() -> service.checkIn(goalId, checkInReq))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the goal owner");

        // Edit refused
        UpdateGoalRequest editReq = new UpdateGoalRequest("New title", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.editGoal(goalId, editReq))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the goal owner");

        // Cancel refused
        assertThatThrownBy(() -> service.cancelGoal(goalId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the goal owner");
    }

    @Test
    @DisplayName("HR admin cannot write to someone else's goal (writes are owner-only)")
    void hrCannotWriteToSomeoneElsesGoal() {
        EmployeeResponse hrResp = mockEmployee(employeeA, "HR-001", "HR", "Admin");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(hrResp));
        when(permissionService.holds("hrms.goal.manage")).thenReturn(true);

        EmployeeGoal goalB = new EmployeeGoal(
                tenantId,
                employeeB,
                null,
                "Goal of B",
                "Description",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                false,
                "b");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goalB));

        GoalCheckInRequest checkInReq = new GoalCheckInRequest(new BigDecimal("50.00"), "HR check-in");
        assertThatThrownBy(() -> service.checkIn(goalId, checkInReq))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the goal owner");
    }

    @Test
    @DisplayName("Employee cannot view another employee's goals without manager or HR permission")
    void unauthorizedViewThrowsAccessDenied() {
        EmployeeResponse empResp = mockEmployee(employeeA, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        when(permissionService.holds("hrms.goal.manage")).thenReturn(false);
        when(reportingLineRepository.findDirectReports(eq(tenantId), eq(employeeA), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.getGoalsForEmployee(employeeB, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not authorized to view goals for employee");
    }

    @Test
    @DisplayName("Manager can view direct reports' goals, and HR can view any employee's goals")
    void managerAndHrCanReadGoals() {
        EmployeeResponse mgrResp = mockEmployee(employeeA, "MGR-001", "Sarah", "Connor");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mgrResp));

        Employee report = mock(Employee.class);
        when(report.getId()).thenReturn(employeeB);
        ReportingLine line = mock(ReportingLine.class);
        when(line.getEmployee()).thenReturn(report);
        when(reportingLineRepository.findDirectReports(eq(tenantId), eq(employeeA), any(LocalDate.class)))
                .thenReturn(List.of(line));

        when(goalRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(eq(tenantId), eq(employeeB)))
                .thenReturn(Collections.emptyList());

        List<EmployeeGoalResponse> goals = service.getGoalsForEmployee(employeeB, null);
        assertThat(goals).isEmpty();

        // Check-ins can also be viewed by manager
        UUID goalId = UUID.randomUUID();
        EmployeeGoal goalB = new EmployeeGoal(
                tenantId,
                employeeB,
                null,
                "Goal of B",
                "Description",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                "%",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                false,
                "b");
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goalB));
        when(checkInRepository.findByTenantIdAndGoalIdOrderByCreatedAtDesc(eq(tenantId), eq(goalId)))
                .thenReturn(Collections.emptyList());

        List<GoalCheckInResponse> checkIns = service.getCheckIns(goalId);
        assertThat(checkIns).isEmpty();
    }
}
