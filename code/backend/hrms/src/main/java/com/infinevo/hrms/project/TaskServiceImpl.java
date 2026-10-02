package com.infinevo.hrms.project;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaskService} (W-41).
 */
@Service
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AssignmentRepository assignmentRepository;
    private final EmployeeService employeeService;
    private final TimesheetUsage timesheetUsage;

    public TaskServiceImpl(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            AssignmentRepository assignmentRepository,
            EmployeeService employeeService,
            TimesheetUsage timesheetUsage) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.assignmentRepository =
                Objects.requireNonNull(assignmentRepository, "assignmentRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.timesheetUsage = Objects.requireNonNull(timesheetUsage, "timesheetUsage must not be null");
    }

    @Override
    public TaskResponse create(UUID projectId, TaskRequest request) {
        UUID tenantId = TenantContext.require();
        projectRepository
                .findByIdAndTenantIdAndDeletedFalse(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + projectId + " found in this tenant"));

        validateTaskRequest(tenantId, projectId, request);

        String actor = ProjectActor.currentActor();
        Task task = new Task();
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setAssigneeEmployeeId(request.assigneeEmployeeId());
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority());
        task.setStatus(request.status() != null ? request.status() : TaskStatus.TODO);
        task.setEstimatedHours(request.estimatedHours());
        task.setCreatedBy(actor);
        task.setUpdatedBy(actor);

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listByProject(UUID projectId, TaskStatus status) {
        UUID tenantId = TenantContext.require();
        projectRepository
                .findByIdAndTenantIdAndDeletedFalse(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + projectId + " found in this tenant"));

        List<Task> tasks = taskRepository.findByTenantIdAndProjectIdAndStatusIn(
                tenantId, projectId, status != null ? EnumSet.of(status) : EnumSet.allOf(TaskStatus.class));
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listMine() {
        UUID tenantId = TenantContext.require();
        Optional<EmployeeResponse> current = employeeService.currentEmployee();
        if (current.isEmpty()) {
            return List.of();
        }

        UUID employeeId = current.get().id();
        List<Task> tasks =
                taskRepository.findAllByTenantIdAndAssigneeEmployeeIdAndDeletedFalseOrderByDueDateAscCreatedAtAsc(
                        tenantId, employeeId);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        Task task = taskRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No task " + id + " found in this tenant"));
        return TaskResponse.from(task);
    }

    @Override
    public TaskResponse update(UUID id, TaskRequest request) {
        UUID tenantId = TenantContext.require();
        Task task = taskRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No task " + id + " found in this tenant"));

        validateTaskRequest(tenantId, task.getProjectId(), request);

        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setAssigneeEmployeeId(request.assigneeEmployeeId());
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        task.setEstimatedHours(request.estimatedHours());
        task.setUpdatedBy(ProjectActor.currentActor());

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    @Override
    public TaskResponse updateStatus(UUID id, TaskStatus status) {
        UUID tenantId = TenantContext.require();
        if (status == null) {
            throw new ValidationException("status", "status must not be null");
        }

        Task task = taskRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No task " + id + " found in this tenant"));

        task.setStatus(status);
        task.setUpdatedBy(ProjectActor.currentActor());
        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        Task task = taskRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No task " + id + " found in this tenant"));

        // Hours were worked on it (W-42.1): a task on a live timesheet stays until that timesheet is gone.
        if (timesheetUsage.taskInUse(tenantId, id)) {
            throw new ResourceInUseException("Task " + id
                    + " is on a timesheet and cannot be deleted until that timesheet is deleted or cancelled");
        }

        task.setDeleted(true);
        task.setUpdatedBy(ProjectActor.currentActor());
        taskRepository.save(task);
    }

    private void validateTaskRequest(UUID tenantId, UUID projectId, TaskRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ValidationException("title", "title is required");
        }
        if (request.priority() == null) {
            throw new ValidationException("priority", "priority is required");
        }

        if (request.assigneeEmployeeId() != null) {
            try {
                employeeService.get(request.assigneeEmployeeId());
            } catch (EmployeeService.NotFoundException e) {
                throw new ValidationException("assignee_employee_id", "Assignee employee not found in current tenant");
            }

            boolean onTeam = assignmentRepository.existsByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(
                    tenantId, projectId, request.assigneeEmployeeId());
            if (!onTeam) {
                throw new ValidationException(
                        "assignee_employee_id", "Assignee employee is not assigned to this project team");
            }
        }
    }
}
