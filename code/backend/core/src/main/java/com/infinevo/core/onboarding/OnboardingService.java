package com.infinevo.core.onboarding;

import com.infinevo.core.employee.Employee;
import java.util.List;
import java.util.UUID;

public interface OnboardingService {

    List<OnboardingTaskTemplateResponse> listTemplates(UUID tenantId);

    OnboardingTaskTemplateResponse createTemplate(UUID tenantId, CreateTemplateRequest request, String actor);

    OnboardingTaskTemplateResponse updateTemplate(
            UUID tenantId, UUID templateId, UpdateTemplateRequest request, String actor);

    void deleteTemplate(UUID tenantId, UUID templateId);

    List<EmployeeOnboardingTaskResponse> assignDefaultTasks(Employee employee, String actor);

    OnboardingProgressResponse getEmployeeProgress(UUID tenantId, UUID employeeId);

    OnboardingProgressResponse assignTasksToEmployee(UUID tenantId, UUID employeeId, String actor);

    EmployeeOnboardingTaskResponse updateTaskStatus(
            UUID tenantId,
            UUID employeeId,
            UUID taskId,
            UpdateTaskStatusRequest request,
            UUID actorUserUuid,
            String actor);
}
