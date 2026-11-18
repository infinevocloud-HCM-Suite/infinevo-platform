package com.infinevo.core.onboarding;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public EmployeeOnboardingController(
            OnboardingService onboardingService, EmployeeService employeeService, PermissionService permissionService) {
        this.onboardingService = Objects.requireNonNull(onboardingService, "onboardingService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    @GetMapping("/employees/{employeeId}/onboarding")
    @RequiresAction(value = "core.onboarding.manage", anyOf = "core.onboarding.read_own")
    public OnboardingProgressResponse getEmployeeProgress(@PathVariable UUID employeeId) {
        UUID tenantId = TenantContext.require();
        assertCanAccess(employeeId, "core.onboarding.manage");
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
            @PathVariable UUID employeeId, @PathVariable UUID taskId, @RequestBody UpdateTaskStatusRequest request) {
        UUID tenantId = TenantContext.require();
        assertCanAccess(employeeId, "core.onboarding.manage");
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

    private void assertCanAccess(UUID targetEmployeeId, String privilegedAction) {
        if (permissionService.currentActions().contains(privilegedAction)) {
            return;
        }
        UUID myEmployeeId =
                employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        if (myEmployeeId == null || !myEmployeeId.equals(targetEmployeeId)) {
            throw new AccessDeniedException("Access denied: cannot access another employee's onboarding tasks");
        }
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
