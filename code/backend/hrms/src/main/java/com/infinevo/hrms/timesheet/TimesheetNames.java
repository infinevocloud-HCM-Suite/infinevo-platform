package com.infinevo.hrms.timesheet;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.hrms.project.Project;
import com.infinevo.hrms.project.ProjectRepository;
import com.infinevo.hrms.project.Task;
import com.infinevo.hrms.project.TaskRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reads the employee, project and task names a page of timesheets shows (W-48.3 §4): one batch read per kind for the
 * whole page, never one per row. An id with no live row gets no entry, so its name is null in the reply.
 */
@Component
public class TimesheetNames {

    /** The names for one page. */
    public record Names(Map<UUID, String> employees, Map<UUID, String> projects, Map<UUID, String> tasks) {
        public static final Names NONE = new Names(Map.of(), Map.of(), Map.of());
    }

    private final EmployeeService employeeService;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public TimesheetNames(
            EmployeeService employeeService, ProjectRepository projectRepository, TaskRepository taskRepository) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
    }

    /** The names on these weeks; call inside the transaction that loaded them. */
    Names forSheets(UUID tenantId, Collection<Timesheet> sheets) {
        Set<UUID> employees = new HashSet<>();
        Set<UUID> projects = new HashSet<>();
        Set<UUID> tasks = new HashSet<>();
        for (Timesheet sheet : sheets) {
            employees.add(sheet.getEmployeeId());
            for (TimesheetProjectEntry entry : sheet.getProjects()) {
                collect(entry, projects, tasks);
            }
        }
        return read(tenantId, employees, projects, tasks);
    }

    /** The names on one project line and its week's owner. */
    Names forEntry(UUID tenantId, TimesheetProjectEntry entry) {
        Set<UUID> projects = new HashSet<>();
        Set<UUID> tasks = new HashSet<>();
        collect(entry, projects, tasks);
        return read(tenantId, Set.of(entry.getTimesheet().getEmployeeId()), projects, tasks);
    }

    private static void collect(TimesheetProjectEntry entry, Set<UUID> projects, Set<UUID> tasks) {
        projects.add(entry.getProjectId());
        for (TimesheetTaskEntry task : entry.getTasks()) {
            tasks.add(task.getTaskId());
        }
    }

    private Names read(UUID tenantId, Set<UUID> employees, Set<UUID> projects, Set<UUID> tasks) {
        Map<UUID, String> employeeNames = employees.isEmpty() ? Map.of() : employeeService.displayNames(employees);
        Map<UUID, String> projectNames = new HashMap<>();
        if (!projects.isEmpty()) {
            for (Project p : projectRepository.findAllByTenantIdAndIdInAndDeletedFalse(tenantId, projects)) {
                projectNames.put(p.getId(), p.getName());
            }
        }
        Map<UUID, String> taskNames = new HashMap<>();
        if (!tasks.isEmpty()) {
            for (Task t : taskRepository.findAllByTenantIdAndIdInAndDeletedFalse(tenantId, tasks)) {
                taskNames.put(t.getId(), t.getTitle());
            }
        }
        return new Names(employeeNames != null ? employeeNames : Map.of(), projectNames, taskNames);
    }
}
