package com.infinevo.core.onboarding;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OnboardingServiceImpl implements OnboardingService {

    private final OnboardingTaskTemplateRepository templateRepository;
    private final EmployeeOnboardingTaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;

    public OnboardingServiceImpl(
            OnboardingTaskTemplateRepository templateRepository,
            EmployeeOnboardingTaskRepository taskRepository,
            EmployeeRepository employeeRepository) {
        this.templateRepository = Objects.requireNonNull(templateRepository, "templateRepository must not be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<OnboardingTaskTemplateResponse> listTemplates(UUID tenantId) {
        return templateRepository.findByTenantIdOrderBySortOrderAsc(tenantId).stream()
                .map(OnboardingTaskTemplateResponse::from)
                .toList();
    }

    @Override
    public OnboardingTaskTemplateResponse createTemplate(UUID tenantId, CreateTemplateRequest request, String actor) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Template title is required");
        }
        OnboardingTaskType taskType = request.taskType() != null ? request.taskType() : OnboardingTaskType.MANUAL_TASK;
        boolean isMandatory = request.isMandatory() == null || request.isMandatory();
        int dueDays = request.dueDaysAfterJoining() != null ? request.dueDaysAfterJoining() : 7;
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;

        OnboardingTaskTemplate template = new OnboardingTaskTemplate(
                tenantId,
                request.title().trim(),
                request.description(),
                taskType,
                request.documentLabel(),
                request.assignedRole(),
                isMandatory,
                dueDays,
                sortOrder,
                actor);

        OnboardingTaskTemplate saved = templateRepository.save(template);
        return OnboardingTaskTemplateResponse.from(saved);
    }

    @Override
    public OnboardingTaskTemplateResponse updateTemplate(
            UUID tenantId, UUID templateId, UpdateTemplateRequest request, String actor) {
        Objects.requireNonNull(request, "request must not be null");
        OnboardingTaskTemplate template = templateRepository
                .findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Onboarding template not found with id: " + templateId));

        if (request.title() != null && !request.title().isBlank()) {
            template.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            template.setDescription(request.description());
        }
        if (request.taskType() != null) {
            template.setTaskType(request.taskType());
        }
        if (request.documentLabel() != null) {
            template.setDocumentLabel(request.documentLabel());
        }
        if (request.assignedRole() != null) {
            template.setAssignedRole(request.assignedRole());
        }
        if (request.isMandatory() != null) {
            template.setMandatory(request.isMandatory());
        }
        if (request.dueDaysAfterJoining() != null) {
            template.setDueDaysAfterJoining(request.dueDaysAfterJoining());
        }
        if (request.sortOrder() != null) {
            template.setSortOrder(request.sortOrder());
        }
        if (request.isActive() != null) {
            template.setActive(request.isActive());
        }
        template.setUpdatedBy(actor);

        OnboardingTaskTemplate saved = templateRepository.save(template);
        return OnboardingTaskTemplateResponse.from(saved);
    }

    @Override
    public void deleteTemplate(UUID tenantId, UUID templateId) {
        OnboardingTaskTemplate template = templateRepository
                .findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Onboarding template not found with id: " + templateId));
        template.setActive(false);
        templateRepository.save(template);
    }

    @Override
    public List<EmployeeOnboardingTaskResponse> assignDefaultTasks(Employee employee, String actor) {
        Objects.requireNonNull(employee, "employee must not be null");
        UUID tenantId = employee.getTenantId();
        List<OnboardingTaskTemplate> templates =
                templateRepository.findByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenantId);
        if (templates.isEmpty()) {
            return List.of();
        }

        LocalDate baseDate = employee.getDateOfJoining() != null ? employee.getDateOfJoining() : LocalDate.now();

        List<EmployeeOnboardingTask> tasksToSave = new ArrayList<>();
        for (OnboardingTaskTemplate template : templates) {
            LocalDate dueDate = baseDate.plusDays(template.getDueDaysAfterJoining());
            EmployeeOnboardingTask task = new EmployeeOnboardingTask(
                    tenantId,
                    employee.getId(),
                    template.getId(),
                    template.getTitle(),
                    template.getDescription(),
                    template.getTaskType(),
                    template.getDocumentLabel(),
                    template.getAssignedRole(),
                    template.isMandatory(),
                    dueDate,
                    actor);
            tasksToSave.add(task);
        }

        return taskRepository.saveAll(tasksToSave).stream()
                .map(EmployeeOnboardingTaskResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingProgressResponse getEmployeeProgress(UUID tenantId, UUID employeeId) {
        List<EmployeeOnboardingTask> tasks =
                taskRepository.findByTenantIdAndEmployeeIdOrderByDueDateAscCreatedAtAsc(tenantId, employeeId);
        List<EmployeeOnboardingTaskResponse> taskResponses =
                tasks.stream().map(EmployeeOnboardingTaskResponse::from).toList();

        int totalTasks = taskResponses.size();
        int completedTasks = (int) taskResponses.stream()
                .filter(t -> t.status() == OnboardingTaskStatus.COMPLETED || t.status() == OnboardingTaskStatus.WAIVED)
                .count();
        int pendingTasks = totalTasks - completedTasks;
        int progressPercentage = totalTasks == 0 ? 100 : (int) Math.round(((double) completedTasks / totalTasks) * 100);

        return new OnboardingProgressResponse(
                employeeId, totalTasks, completedTasks, pendingTasks, progressPercentage, taskResponses);
    }

    @Override
    public OnboardingProgressResponse assignTasksToEmployee(UUID tenantId, UUID employeeId, String actor) {
        Employee employee = employeeRepository
                .findById(employeeId)
                .filter(e -> e.getTenantId().equals(tenantId) && !e.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("Employee not found with id: " + employeeId));

        long existingCount = taskRepository.countByTenantIdAndEmployeeId(tenantId, employeeId);
        if (existingCount == 0) {
            assignDefaultTasks(employee, actor);
        }
        return getEmployeeProgress(tenantId, employeeId);
    }

    @Override
    public EmployeeOnboardingTaskResponse updateTaskStatus(
            UUID tenantId,
            UUID employeeId,
            UUID taskId,
            UpdateTaskStatusRequest request,
            UUID actorUserUuid,
            String actor) {
        Objects.requireNonNull(request, "request must not be null");
        EmployeeOnboardingTask task = taskRepository
                .findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Onboarding task not found with id: " + taskId));

        if (!task.getEmployeeId().equals(employeeId)) {
            throw new IllegalArgumentException("Task does not belong to employee: " + employeeId);
        }

        if (request.status() != null) {
            task.setStatus(request.status());
            if (request.status() == OnboardingTaskStatus.COMPLETED || request.status() == OnboardingTaskStatus.WAIVED) {
                task.setCompletedAt(Instant.now());
                task.setCompletedBy(actorUserUuid);
            }
        }
        if (request.documentId() != null) {
            task.setDocumentId(request.documentId());
        }
        if (request.remarks() != null) {
            task.setRemarks(request.remarks());
        }
        task.setUpdatedBy(actor);

        EmployeeOnboardingTask saved = taskRepository.save(task);
        return EmployeeOnboardingTaskResponse.from(saved);
    }
}
