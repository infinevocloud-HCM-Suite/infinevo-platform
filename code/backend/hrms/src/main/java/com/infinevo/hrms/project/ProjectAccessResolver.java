package com.infinevo.hrms.project;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Access resolution for project reads and task status updates (W-41 §4).
 *
 * <p>Spec §4 rules:
 * <ul>
 *   <li>Project and team read endpoints (GET /projects/{id}, GET /projects/{id}/assignments,
 *       GET /projects/{id}/tasks) are accessible to callers holding {@code hrms.project.read},
 *       callers holding {@code hrms.project.read_team} who manage the project, or callers
 *       holding {@code hrms.project.read_own} who are assigned to the project.</li>
 *   <li>Task status updates (PUT /tasks/{id}/status) are accessible to callers holding
 *       {@code hrms.project.manage}, or callers holding {@code hrms.project.read_own} who
 *       are the assignee of the task.</li>
 *   <li>Project list (GET /projects) with {@code read_team} requires {@code managed=true}.</li>
 * </ul>
 */
@Component
public class ProjectAccessResolver {

    public static final String ACTION_MANAGE = "hrms.project.manage";
    public static final String ACTION_READ = "hrms.project.read";
    public static final String ACTION_READ_TEAM = "hrms.project.read_team";
    public static final String ACTION_READ_OWN = "hrms.project.read_own";

    private final PermissionService permissionService;
    private final EmployeeService employeeService;
    private final ProjectService projectService;

    public ProjectAccessResolver(
            PermissionService permissionService, EmployeeService employeeService, ProjectService projectService) {
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.projectService = Objects.requireNonNull(projectService, "projectService must not be null");
    }

    /**
     * Checks if the caller is authorized to list projects with the given managed flag.
     * Callers holding only {@code hrms.project.read_team} must pass {@code managed=true}.
     *
     * @param managed whether the query is filtered to managed projects
     * @throws PermissionDeniedException if caller lacks permission
     */
    public void checkCanListProjects(boolean managed) {
        if (!permissionService.holds(ACTION_READ)) {
            if (!managed) {
                throw new PermissionDeniedException(ACTION_READ);
            }
        }
    }

    /**
     * Checks if the caller is authorized to read the specified project, returning the project response.
     *
     * @param projectId the project id
     * @return the project response
     * @throws ResourceNotFoundException if the project does not exist
     * @throws PermissionDeniedException if the caller lacks authorization
     */
    public ProjectResponse checkReadable(UUID projectId) {
        ProjectResponse project = projectService.get(projectId);
        if (permissionService.holds(ACTION_READ)) {
            return project;
        }
        Optional<UUID> callerEmployeeId = currentEmployeeId();
        if (callerEmployeeId.isPresent()) {
            UUID empId = callerEmployeeId.get();
            if (permissionService.holds(ACTION_READ_TEAM)
                    && project.managerEmployeeId() != null
                    && project.managerEmployeeId().equals(empId)) {
                return project;
            }
            if (permissionService.holds(ACTION_READ_OWN)
                    && project.teamMemberIds() != null
                    && project.teamMemberIds().contains(empId)) {
                return project;
            }
        }
        throw new PermissionDeniedException(ACTION_READ);
    }

    /**
     * Checks if the caller is authorized to update the status of the specified task.
     *
     * @param task the task response
     * @throws PermissionDeniedException if caller is neither a project manager nor the assignee
     */
    public void checkCanUpdateTaskStatus(TaskResponse task) {
        if (permissionService.holds(ACTION_MANAGE)) {
            return;
        }
        if (permissionService.holds(ACTION_READ_OWN)) {
            Optional<UUID> callerEmployeeId = currentEmployeeId();
            if (callerEmployeeId.isPresent()
                    && task.assigneeEmployeeId() != null
                    && task.assigneeEmployeeId().equals(callerEmployeeId.get())) {
                return;
            }
        }
        throw new PermissionDeniedException(ACTION_MANAGE);
    }

    public Optional<UUID> currentEmployeeId() {
        return employeeService.currentEmployee().map(EmployeeResponse::id);
    }
}
