package com.infinevo.hrms.project;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ProjectService} (W-41).
 */
@Service
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final AssignmentRepository assignmentRepository;
    private final EmployeeService employeeService;
    private final TimesheetUsage timesheetUsage;

    public ProjectServiceImpl(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            AssignmentRepository assignmentRepository,
            EmployeeService employeeService,
            TimesheetUsage timesheetUsage) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.assignmentRepository =
                Objects.requireNonNull(assignmentRepository, "assignmentRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.timesheetUsage = Objects.requireNonNull(timesheetUsage, "timesheetUsage must not be null");
    }

    @Override
    public ProjectResponse create(ProjectRequest request) {
        UUID tenantId = TenantContext.require();
        validateProjectRequest(tenantId, request, null);

        String actor = ProjectActor.currentActor();
        Project project = new Project();
        project.setTenantId(tenantId);
        project.setName(request.name().trim());
        project.setCategory(request.category());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setPriority(request.priority());
        project.setStatus(request.status() != null ? request.status() : ProjectStatus.STARTED);
        project.setProgress(0);
        project.setBudget(request.budget());
        project.setManagerEmployeeId(request.managerEmployeeId());
        project.setCreatedBy(actor);
        project.setUpdatedBy(actor);

        Project saved = projectRepository.save(project);
        return respond(tenantId, List.of(saved)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> list(ProjectStatus status, String search, boolean managed) {
        UUID tenantId = TenantContext.require();
        boolean anyManager = !managed;
        // A placeholder that matches no manager: the parameter is never null (see ProjectRepository).
        UUID managerEmployeeId = new UUID(0L, 0L);

        if (managed) {
            Optional<EmployeeResponse> current = employeeService.currentEmployee();
            if (current.isEmpty()) {
                return List.of();
            }
            managerEmployeeId = current.get().id();
        }

        List<Project> projects = projectRepository.searchProjects(
                tenantId,
                status != null ? EnumSet.of(status) : EnumSet.allOf(ProjectStatus.class),
                anyManager,
                managerEmployeeId,
                ProjectLikePattern.contains(search));

        return respond(tenantId, projects);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> listMine() {
        UUID tenantId = TenantContext.require();
        Optional<EmployeeResponse> current = employeeService.currentEmployee();
        if (current.isEmpty()) {
            return List.of();
        }

        UUID employeeId = current.get().id();
        List<Assignment> assignments =
                assignmentRepository.findAllByTenantIdAndEmployeeIdAndDeletedFalse(tenantId, employeeId);
        Set<UUID> projectIds =
                assignments.stream().map(Assignment::getProjectId).collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return List.of();
        }

        List<Project> projects = projectRepository.findAllByTenantIdAndIdInAndDeletedFalse(tenantId, projectIds);
        return respond(tenantId, projects);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        Project project = projectRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + id + " found in this tenant"));

        return respond(tenantId, List.of(project)).get(0);
    }

    @Override
    public ProjectResponse update(UUID id, ProjectRequest request) {
        UUID tenantId = TenantContext.require();
        Project project = projectRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + id + " found in this tenant"));

        validateProjectRequest(tenantId, request, id);

        project.setName(request.name().trim());
        project.setCategory(request.category());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setPriority(request.priority());
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        project.setBudget(request.budget());
        project.setManagerEmployeeId(request.managerEmployeeId());
        project.setUpdatedBy(ProjectActor.currentActor());

        Project saved = projectRepository.save(project);
        return respond(tenantId, List.of(saved)).get(0);
    }

    @Override
    public ProjectResponse updateStatus(UUID id, ProjectStatus status) {
        UUID tenantId = TenantContext.require();
        if (status == null) {
            throw new ValidationException("status", "status must not be null");
        }

        Project project = projectRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + id + " found in this tenant"));

        project.setStatus(status);
        project.setUpdatedBy(ProjectActor.currentActor());
        Project saved = projectRepository.save(project);
        return respond(tenantId, List.of(saved)).get(0);
    }

    @Override
    public ProjectResponse updateProgress(UUID id, int progress) {
        UUID tenantId = TenantContext.require();
        if (progress < 0 || progress > 100) {
            throw new ValidationException("progress", "progress must be between 0 and 100");
        }

        Project project = projectRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + id + " found in this tenant"));

        project.setProgress(progress);
        project.setUpdatedBy(ProjectActor.currentActor());
        Project saved = projectRepository.save(project);
        return respond(tenantId, List.of(saved)).get(0);
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        Project project = projectRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + id + " found in this tenant"));

        // Hours were worked on it (W-42.1): a project on a live timesheet stays until that timesheet is gone.
        if (timesheetUsage.projectInUse(tenantId, id)) {
            throw new ResourceInUseException("Project " + id
                    + " is on a timesheet and cannot be deleted until that timesheet is deleted or cancelled");
        }

        String actor = ProjectActor.currentActor();
        project.setDeleted(true);
        project.setUpdatedBy(actor);
        projectRepository.save(project);

        List<Task> tasks = taskRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenantId, id);
        for (Task t : tasks) {
            t.setDeleted(true);
            t.setUpdatedBy(actor);
        }
        taskRepository.saveAll(tasks);

        List<Assignment> assignments = assignmentRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenantId, id);
        for (Assignment a : assignments) {
            a.setDeleted(true);
            a.setUpdatedBy(actor);
        }
        assignmentRepository.saveAll(assignments);
    }

    private void validateProjectRequest(UUID tenantId, ProjectRequest request, UUID existingId) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("name", "name is required");
        }
        if (request.priority() == null) {
            throw new ValidationException("priority", "priority is required");
        }

        String trimmedName = request.name().trim();
        boolean nameExists = existingId == null
                ? projectRepository.existsByTenantIdAndNameIgnoreCaseAndDeletedFalse(tenantId, trimmedName)
                : projectRepository.existsByTenantIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(
                        tenantId, trimmedName, existingId);
        if (nameExists) {
            throw new ValidationException("name", "A project named '" + trimmedName + "' already exists");
        }

        if (request.startDate() != null
                && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new ValidationException("end_date", "endDate must be on or after startDate");
        }

        if (request.budget() != null && request.budget().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("budget", "budget must be non-negative");
        }

        if (request.managerEmployeeId() != null) {
            try {
                employeeService.get(request.managerEmployeeId());
            } catch (EmployeeService.NotFoundException e) {
                throw new ValidationException("manager_employee_id", "Manager employee not found in current tenant");
            }
        }
    }

    /**
     * Builds responses for a page of projects with one batch name read for every manager and team
     * member on the page (W-48.1 section 4); an id with no employee gets a {@code null} name.
     */
    private List<ProjectResponse> respond(UUID tenantId, List<Project> projects) {
        java.util.Map<UUID, List<UUID>> teams = new java.util.LinkedHashMap<>();
        Set<UUID> ids = new java.util.HashSet<>();
        for (Project p : projects) {
            List<UUID> teamIds =
                    assignmentRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenantId, p.getId()).stream()
                            .map(Assignment::getEmployeeId)
                            .toList();
            teams.put(p.getId(), teamIds);
            ids.addAll(teamIds);
            if (p.getManagerEmployeeId() != null) {
                ids.add(p.getManagerEmployeeId());
            }
        }
        java.util.Map<UUID, String> names = ids.isEmpty() ? java.util.Map.of() : employeeService.displayNames(ids);
        return projects.stream()
                .map(p -> ProjectResponse.from(p, teams.get(p.getId()), names))
                .toList();
    }
}
