package com.infinevo.core.onboarding;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineKind;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EmployeeOnboardingController {

    private final OnboardingService onboardingService;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;
    private final ReportingLineRepository reportingLineRepository;

    public EmployeeOnboardingController(
            OnboardingService onboardingService,
            EmployeeService employeeService,
            PermissionService permissionService,
            ReportingLineRepository reportingLineRepository) {
        this.onboardingService = Objects.requireNonNull(onboardingService, "onboardingService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
    }

    @GetMapping("/employees/{employeeId}/onboarding")
    @RequiresAction(value = "core.onboarding.manage", anyOf = "core.onboarding.read_own")
    public OnboardingProgressResponse getEmployeeProgress(@PathVariable UUID employeeId) {
        UUID tenantId = TenantContext.require();
        assertCanAccess(tenantId, employeeId, "core.onboarding.manage");
        return onboardingService.getEmployeeProgress(tenantId, employeeId);
    }

    @PostMapping("/employees/{employeeId}/onboarding/assign")
    @RequiresAction("core.onboarding.manage")
    public OnboardingProgressResponse assignTasks(@PathVariable UUID employeeId) {
        UUID tenantId = TenantContext.require();
        return onboardingService.assignTasksToEmployee(tenantId, employeeId, currentActor());
    }

    @PatchMapping("/employees/{employeeId}/onboarding/{taskId}")
    @RequiresAction(value = "core.onboarding.manage", anyOf = "core.onboarding.update_own")
    public EmployeeOnboardingTaskResponse updateTask(
            @PathVariable UUID employeeId,
            @PathVariable UUID taskId,
            @RequestBody UpdateTaskStatusRequest request) {
        UUID tenantId = TenantContext.require();
        assertCanAccess(tenantId, employeeId, "core.onboarding.manage");
        UUID actorUuid = currentUserId();
        return onboardingService.updateTaskStatus(tenantId, employeeId, taskId, request, actorUuid, currentActor());
    }

    @GetMapping("/me/onboarding")
    @RequiresAction("core.onboarding.read_own")
    public OnboardingProgressResponse getMyProgress() {
        UUID tenantId = TenantContext.require();
        UUID myEmployeeId = employeeService
                .currentEmployee()
                .map(EmployeeResponse::id)
                .orElseThrow(() -> new EmployeeService.NotFoundException("No employee profile linked to current user"));
        return onboardingService.getEmployeeProgress(tenantId, myEmployeeId);
    }

    private void assertCanAccess(UUID tenantId, UUID targetEmployeeId, String privilegedAction) {
        if (permissionService.currentActions().contains(privilegedAction)) {
            return;
        }
        UUID myEmployeeId =
                employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        if (myEmployeeId != null && myEmployeeId.equals(targetEmployeeId)) {
            return;
        }
        if (myEmployeeId != null && isPrimaryManagerOf(tenantId, myEmployeeId, targetEmployeeId)) {
            return;
        }
        throw new AccessDeniedException("Access denied: cannot access another employee's onboarding tasks");
    }

    private boolean isPrimaryManagerOf(UUID tenantId, UUID managerEmployeeId, UUID targetEmployeeId) {
        return reportingLineRepository
                .findCurrentOpenLine(tenantId, targetEmployeeId, ReportingLineKind.PRIMARY)
                .map(ReportingLine::getManager)
                .map(Employee::getId)
                .filter(managerEmployeeId::equals)
                .isPresent();
    }

    @ExceptionHandler({NoSuchElementException.class, EmployeeService.NotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(ApiError.FORBIDDEN, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
