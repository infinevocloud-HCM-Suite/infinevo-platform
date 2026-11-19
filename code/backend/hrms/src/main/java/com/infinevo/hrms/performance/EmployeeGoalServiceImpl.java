package com.infinevo.hrms.performance;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for managing employee goals and OKR key results (W-106.2).
 */
@Service
@Transactional
public class EmployeeGoalServiceImpl implements EmployeeGoalService {

    private final EmployeeGoalRepository goalRepository;
    private final EmployeeRepository employeeRepository;
    private final PerformanceCycleRepository cycleRepository;
    private final ReportingLineRepository reportingLineRepository;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;

    public EmployeeGoalServiceImpl(
            EmployeeGoalRepository goalRepository,
            EmployeeRepository employeeRepository,
            PerformanceCycleRepository cycleRepository,
            ReportingLineRepository reportingLineRepository,
            EmployeeService employeeService,
            PermissionService permissionService) {
        this.goalRepository = Objects.requireNonNull(goalRepository, "goalRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.cycleRepository = Objects.requireNonNull(cycleRepository, "cycleRepository must not be null");
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    @Override
    public EmployeeGoalResponse createGoal(CreateGoalRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();
        validateCreateGoalRequest(request);

        String cycleName = null;
        if (request.cycleId() != null) {
            PerformanceCycle cycle = cycleRepository
                    .findByIdAndTenantId(request.cycleId(), tenantId)
                    .orElseThrow(
                            () -> new IllegalArgumentException("Performance cycle not found: " + request.cycleId()));
            cycleName = cycle.getName();
        }

        EmployeeGoal goal = new EmployeeGoal(
                tenantId,
                current.id(),
                request.cycleId(),
                request.title(),
                request.description(),
                request.category(),
                request.metricType(),
                request.targetValue(),
                request.currentValue(),
                request.metricUnit(),
                request.progressPercentage(),
                request.startDate(),
                request.dueDate(),
                request.status(),
                currentActor());

        EmployeeGoal saved = goalRepository.save(goal);
        String empFullName =
                (current.firstName() + " " + (current.lastName() != null ? current.lastName() : "")).trim();
        return mapSingleGoalToResponse(tenantId, saved, empFullName, current.employeeNumber(), cycleName);
    }

    @Override
    public EmployeeGoalResponse updateProgress(UUID goalId, UpdateGoalProgressRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();

        EmployeeGoal goal = goalRepository
                .findByIdAndTenantId(goalId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Employee goal not found: " + goalId));

        boolean isOwner = goal.getEmployeeId().equals(current.id());
        boolean isHRAdmin = permissionService.holds("hrms.goal.manage");
        boolean isManager = isManagerOf(tenantId, current.id(), goal.getEmployeeId());

        if (!isOwner && !isHRAdmin && !isManager) {
            throw new AccessDeniedException("Not authorized to update this goal");
        }

        goal.updateProgress(
                request != null ? request.currentValue() : null,
                request != null ? request.progressPercentage() : null,
                request != null ? request.status() : null,
                currentActor());

        EmployeeGoal saved = goalRepository.save(goal);
        return mapGoalToResponse(tenantId, saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeGoalResponse> getMyGoals() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();
        List<EmployeeGoal> goals =
                goalRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, current.id());
        return mapGoalsToResponses(tenantId, goals);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeGoalResponse> getTeamGoals() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();

        List<ReportingLine> directReports =
                reportingLineRepository.findDirectReports(tenantId, current.id(), LocalDate.now());
        if (directReports.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> reportEmpIds = directReports.stream()
                .filter(rl -> rl.getEmployee() != null)
                .map(rl -> rl.getEmployee().getId())
                .distinct()
                .toList();

        if (reportEmpIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<EmployeeGoal> goals =
                goalRepository.findByTenantIdAndEmployeeIdInOrderByCreatedAtDesc(tenantId, reportEmpIds);
        return mapGoalsToResponses(tenantId, goals);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeGoalResponse> getGoalsForEmployee(UUID employeeId) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();

        boolean isSelf = current.id().equals(employeeId);
        boolean isHRAdmin = permissionService.holds("hrms.goal.manage");
        boolean isManager = isManagerOf(tenantId, current.id(), employeeId);

        if (!isSelf && !isHRAdmin && !isManager) {
            throw new AccessDeniedException("Not authorized to view goals for employee: " + employeeId);
        }

        List<EmployeeGoal> goals = goalRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employeeId);
        return mapGoalsToResponses(tenantId, goals);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeGoalResponse> getGoalsForCycle(UUID cycleId) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();

        List<EmployeeGoal> goals;
        if (permissionService.holds("hrms.goal.manage")) {
            goals = goalRepository.findByTenantIdAndCycleIdOrderByCreatedAtDesc(tenantId, cycleId);
        } else {
            goals = goalRepository.findByTenantIdAndEmployeeIdAndCycleIdOrderByCreatedAtDesc(
                    tenantId, current.id(), cycleId);
        }
        return mapGoalsToResponses(tenantId, goals);
    }

    private boolean isManagerOf(UUID tenantId, UUID managerId, UUID employeeId) {
        List<ReportingLine> lines = reportingLineRepository.findDirectReports(tenantId, managerId, LocalDate.now());
        return lines.stream()
                .anyMatch(rl -> rl.getEmployee() != null
                        && employeeId.equals(rl.getEmployee().getId()));
    }

    private void validateCreateGoalRequest(CreateGoalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Goal title is required");
        }
        if (request.startDate() == null || request.dueDate() == null) {
            throw new IllegalArgumentException("startDate and dueDate are required");
        }
        if (request.dueDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Due date must not be before start date");
        }
    }

    private List<EmployeeGoalResponse> mapGoalsToResponses(UUID tenantId, List<EmployeeGoal> goals) {
        if (goals.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> empIds = goals.stream().map(EmployeeGoal::getEmployeeId).collect(Collectors.toSet());
        Set<UUID> cycleIds = goals.stream()
                .map(EmployeeGoal::getCycleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Employee> empMap = employeeRepository.findByTenantIdAndIdIn(tenantId, empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));

        Map<UUID, String> cycleMap = cycleIds.isEmpty()
                ? Collections.emptyMap()
                : cycleRepository.findAllById(cycleIds).stream()
                        .filter(c -> c.getTenantId().equals(tenantId))
                        .collect(Collectors.toMap(PerformanceCycle::getId, PerformanceCycle::getName, (a, b) -> a));

        return goals.stream()
                .map(goal -> {
                    Employee emp = empMap.get(goal.getEmployeeId());
                    String empName = formatEmployeeName(emp);
                    String empNumber = emp != null ? emp.getEmployeeNumber() : "";
                    String cycleName = goal.getCycleId() != null ? cycleMap.get(goal.getCycleId()) : null;
                    return EmployeeGoalResponse.from(goal, empName, empNumber, cycleName);
                })
                .toList();
    }

    private EmployeeGoalResponse mapGoalToResponse(UUID tenantId, EmployeeGoal goal) {
        Employee emp = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(goal.getEmployeeId(), tenantId)
                .orElse(null);
        String empName = formatEmployeeName(emp);
        String empNumber = emp != null ? emp.getEmployeeNumber() : "";
        String cycleName = null;
        if (goal.getCycleId() != null) {
            cycleName = cycleRepository
                    .findByIdAndTenantId(goal.getCycleId(), tenantId)
                    .map(PerformanceCycle::getName)
                    .orElse(null);
        }
        return EmployeeGoalResponse.from(goal, empName, empNumber, cycleName);
    }

    private EmployeeGoalResponse mapSingleGoalToResponse(
            UUID tenantId, EmployeeGoal goal, String employeeName, String employeeNumber, String cycleName) {
        return EmployeeGoalResponse.from(goal, employeeName, employeeNumber, cycleName);
    }

    private String formatEmployeeName(Employee e) {
        if (e == null) {
            return "Unknown";
        }
        String first = e.getFirstName() != null ? e.getFirstName() : "";
        String last = e.getLastName() != null ? e.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "Unknown" : full;
    }

    private EmployeeResponse requireCurrentEmployee() {
        return employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        String name = auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
