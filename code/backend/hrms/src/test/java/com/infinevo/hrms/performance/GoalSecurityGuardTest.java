package com.infinevo.hrms.performance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class GoalSecurityGuardTest {

    private EmployeeGoalRepository goalRepository;
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
    @DisplayName("User without linked employee profile is rejected with AccessDeniedException")
    void unlinkedUserThrowsAccessDenied() {
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyGoals())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No employee profile linked to current user account");
    }

    @Test
    @DisplayName("Employee cannot update another employee's goal without manager or HR permission")
    void unauthorizedUpdateThrowsAccessDenied() {
        EmployeeResponse empResp = mockEmployee(employeeA, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        // Goal belongs to employeeB
        EmployeeGoal goalB = new EmployeeGoal(
                tenantId,
                employeeB,
                null,
                "Goal of B",
                "Description",
                GoalCategory.INDIVIDUAL,
                MetricType.PERCENTAGE,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "%",
                0,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                GoalStatus.IN_PROGRESS,
                "b");

        UUID goalId = UUID.randomUUID();
        when(goalRepository.findByIdAndTenantId(eq(goalId), eq(tenantId))).thenReturn(Optional.of(goalB));
        when(permissionService.holds("hrms.goal.manage")).thenReturn(false);
        when(reportingLineRepository.findDirectReports(eq(tenantId), eq(employeeA), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        UpdateGoalProgressRequest req = new UpdateGoalProgressRequest(
                new BigDecimal("50.00"), 50, GoalStatus.IN_PROGRESS, "Unauthorized check-in");

        assertThatThrownBy(() -> service.updateProgress(goalId, req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not authorized to update this goal");
    }

    @Test
    @DisplayName("Employee cannot view another employee's goals without manager or HR permission")
    void unauthorizedViewThrowsAccessDenied() {
        EmployeeResponse empResp = mockEmployee(employeeA, "EMP-001", "Alex", "Vance");
        when(employeeService.currentEmployee()).thenReturn(Optional.of(empResp));

        when(permissionService.holds("hrms.goal.manage")).thenReturn(false);
        when(reportingLineRepository.findDirectReports(eq(tenantId), eq(employeeA), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.getGoalsForEmployee(employeeB))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not authorized to view goals for employee");
    }
}
