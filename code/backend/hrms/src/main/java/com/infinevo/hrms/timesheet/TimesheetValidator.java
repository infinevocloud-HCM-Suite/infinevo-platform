package com.infinevo.hrms.timesheet;

import com.infinevo.hrms.project.AssignmentRepository;
import com.infinevo.hrms.project.ProjectRepository;
import com.infinevo.hrms.project.Task;
import com.infinevo.hrms.project.TaskRepository;
import com.infinevo.hrms.project.ValidationException;
import com.infinevo.hrms.timesheet.TimesheetRequest.ProjectLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.TaskLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule a timesheet request must meet before it is saved (W-42.1 §4, "Validation"): the shape rules of
 * {@link TimesheetRules}, then what needs the database. Shared by the draft save (W-42.1) and the resubmit of rejected
 * projects (W-42.3), which must meet the same rules. All errors are reported together, by field.
 */
@Component
@Transactional(readOnly = true)
class TimesheetValidator {

    private static final String NOT_ASSIGNED = "No such project, or you are not assigned to it";
    private static final String NO_SUCH_TASK = "No such task on this project";

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final AssignmentRepository assignmentRepository;

    TimesheetValidator(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            AssignmentRepository assignmentRepository) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.assignmentRepository =
                Objects.requireNonNull(assignmentRepository, "assignmentRepository must not be null");
    }

    /** Validates a whole-week request. */
    void validate(UUID tenantId, UUID employeeId, TimesheetRequest request) {
        validate(tenantId, employeeId, request, Map.of());
    }

    /**
     * Validates a request that is part of a week: {@code carried} is the hours per date already held on lines the
     * request does not replace.
     *
     * @throws ValidationException listing every broken rule, by field
     */
    void validate(UUID tenantId, UUID employeeId, TimesheetRequest request, Map<LocalDate, BigDecimal> carried) {
        Map<String, String> errors = new LinkedHashMap<>(TimesheetRules.validate(request, carried));
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        List<ProjectLine> projects = request.projects();
        Set<UUID> projectIds = projects.stream().map(ProjectLine::projectId).collect(Collectors.toSet());
        Set<UUID> liveProjects =
                projectRepository.findAllByTenantIdAndIdInAndDeletedFalse(tenantId, projectIds).stream()
                        .map(p -> p.getId())
                        .collect(Collectors.toSet());
        Set<UUID> assigned =
                assignmentRepository.findAllByTenantIdAndEmployeeIdAndDeletedFalse(tenantId, employeeId).stream()
                        .map(a -> a.getProjectId())
                        .collect(Collectors.toSet());

        Set<UUID> taskIds = new HashSet<>();
        for (ProjectLine project : projects) {
            project.tasks().forEach(t -> taskIds.add(t.taskId()));
        }
        Map<UUID, Task> liveTasks = taskRepository.findAllByTenantIdAndIdInAndDeletedFalse(tenantId, taskIds).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        for (int p = 0; p < projects.size(); p++) {
            ProjectLine project = projects.get(p);
            String pk = "projects[" + p + "]";
            // One message for "no such project" and "not yours", so a probe cannot tell the two apart.
            if (!liveProjects.contains(project.projectId()) || !assigned.contains(project.projectId())) {
                errors.put(pk + ".project_id", NOT_ASSIGNED);
                continue;
            }
            List<TaskLine> tasks = project.tasks();
            for (int t = 0; t < tasks.size(); t++) {
                Task task = liveTasks.get(tasks.get(t).taskId());
                if (task == null || !project.projectId().equals(task.getProjectId())) {
                    errors.put(pk + ".tasks[" + t + "].task_id", NO_SUCH_TASK);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
