package com.infinevo.hrms.timesheet;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.hrms.project.AssignmentRepository;
import com.infinevo.hrms.project.ProjectRepository;
import com.infinevo.hrms.project.ResourceNotFoundException;
import com.infinevo.hrms.project.Task;
import com.infinevo.hrms.project.TaskRepository;
import com.infinevo.hrms.project.ValidationException;
import com.infinevo.hrms.timesheet.TimesheetRequest.DayLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.ProjectLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.TaskLine;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TimesheetService} (W-42.1).
 *
 * <p>Concurrency: two saves for the same week race for {@code uk_timesheet_tenant_employee_week}; the loser gets the
 * same 409 as one that finds the week taken first. A replace deletes the old lines and flushes before it inserts the
 * new ones, so a line that keeps its project or task does not meet its own unique index.
 */
@Service
@Transactional
public class TimesheetServiceImpl implements TimesheetService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetServiceImpl.class);

    static final String ACTION_SUBMIT = "hrms.timesheet.submit";
    static final String ACTION_READ_OWN = "hrms.timesheet.read_own";
    private static final String UNIQUE_WEEK = "uk_timesheet_tenant_employee_week";
    private static final String NOT_ASSIGNED = "No such project, or you are not assigned to it";
    private static final String NO_SUCH_TASK = "No such task on this project";
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final TimesheetRepository timesheetRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final AssignmentRepository assignmentRepository;
    private final EmployeeService employeeService;
    private final Clock clock;

    @Autowired
    public TimesheetServiceImpl(
            TimesheetRepository timesheetRepository,
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            AssignmentRepository assignmentRepository,
            EmployeeService employeeService) {
        this(
                timesheetRepository,
                projectRepository,
                taskRepository,
                assignmentRepository,
                employeeService,
                Clock.systemDefaultZone());
    }

    /** For tests: a fixed clock for "this week". Spring uses the other constructor, as there is no Clock bean. */
    TimesheetServiceImpl(
            TimesheetRepository timesheetRepository,
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            AssignmentRepository assignmentRepository,
            EmployeeService employeeService,
            Clock clock) {
        this.timesheetRepository = Objects.requireNonNull(timesheetRepository, "timesheetRepository must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.assignmentRepository =
                Objects.requireNonNull(assignmentRepository, "assignmentRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public TimesheetResponse create(TimesheetRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse me = currentEmployee(ACTION_SUBMIT);
        validate(tenantId, me.id(), request);

        LocalDate week = request.weekStartDate();
        if (timesheetRepository.existsByTenantIdAndEmployeeIdAndWeekStartDateAndStatusNot(
                tenantId, me.id(), week, TimesheetStatus.CANCELLED)) {
            throw weekTaken(week);
        }

        String actor = currentActor();
        Timesheet sheet = new Timesheet(tenantId, me.id(), week, actor);
        applyLines(sheet, request, actor);
        try {
            sheet = timesheetRepository.saveAndFlush(sheet);
        } catch (DataIntegrityViolationException e) {
            if (isWeekTaken(e)) {
                throw weekTaken(week);
            }
            throw e;
        }
        log.info("Timesheet {} created as a draft for week {} in tenant {}", sheet.getId(), week, tenantId);
        return TimesheetResponse.from(sheet);
    }

    @Override
    public TimesheetResponse replace(UUID id, TimesheetRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse me = currentEmployee(ACTION_SUBMIT);
        Timesheet sheet = owned(tenantId, me.id(), id);
        requireDraft(sheet, "changed");

        if (request != null
                && request.weekStartDate() != null
                && !request.weekStartDate().equals(sheet.getWeekStartDate())) {
            throw new ValidationException(
                    "week_start_date",
                    "week_start_date cannot be changed; delete the draft and create one for the other week");
        }
        TimesheetRequest body = request;
        validate(tenantId, me.id(), body);

        String actor = currentActor();
        // Old lines go first, in their own flush: a line that keeps its project or task would otherwise be inserted
        // before its predecessor is deleted and meet its own unique index.
        sheet.clearProjects();
        timesheetRepository.saveAndFlush(sheet);
        applyLines(sheet, body, actor);
        sheet.touch(actor);
        sheet = timesheetRepository.saveAndFlush(sheet);
        log.info("Timesheet {} replaced in tenant {}", sheet.getId(), tenantId);
        return TimesheetResponse.from(sheet);
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse me = currentEmployee(ACTION_SUBMIT);
        Timesheet sheet = owned(tenantId, me.id(), id);
        requireDraft(sheet, "deleted");
        timesheetRepository.delete(sheet);
        timesheetRepository.flush();
        log.info("Draft timesheet {} deleted in tenant {}", id, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public TimesheetResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse me = currentEmployee(ACTION_READ_OWN);
        return TimesheetResponse.from(owned(tenantId, me.id(), id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimesheetResponse> listMine(LocalDate from, LocalDate to, TimesheetStatus status, UUID projectId) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse me = currentEmployee(ACTION_READ_OWN);
        LocalDate lower = from != null ? from : EARLIEST;
        LocalDate upper = to != null ? to : LATEST;
        if (lower.isAfter(upper)) {
            throw new ValidationException("from", "from must not be after to");
        }
        return timesheetRepository
                .findByTenantIdAndEmployeeIdAndWeekStartDateBetweenOrderByWeekStartDateDesc(
                        tenantId, me.id(), lower, upper)
                .stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t ->
                        projectId == null || t.getProjects().stream().anyMatch(p -> projectId.equals(p.getProjectId())))
                .map(TimesheetResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TimesheetResponse> forWeek(LocalDate weekStart) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse me = currentEmployee(ACTION_READ_OWN);
        LocalDate week = weekStart != null ? weekStart : TimesheetRules.mondayOf(LocalDate.now(clock));
        if (!TimesheetRules.isMonday(week)) {
            throw new ValidationException("week_start", "week_start must be a Monday; the week runs Monday to Sunday");
        }
        return timesheetRepository
                .findByTenantIdAndEmployeeIdAndWeekStartDateAndStatusNot(
                        tenantId, me.id(), week, TimesheetStatus.CANCELLED)
                .map(TimesheetResponse::from);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    /**
     * The login's employee. A login with no employee record is the permission error, which is a 403: it must not be
     * the 500 that W-25 gave for the same case on {@code /me/employee}.
     */
    private EmployeeResponse currentEmployee(String action) {
        return employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(action));
    }

    /** The caller's own timesheet. Not theirs, or not there, is the same 404, so ids do not leak. */
    private Timesheet owned(UUID tenantId, UUID employeeId, UUID id) {
        return timesheetRepository
                .findByIdAndTenantIdAndEmployeeId(id, tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("No timesheet " + id));
    }

    private static void requireDraft(Timesheet sheet, String verb) {
        if (sheet.getStatus() != TimesheetStatus.DRAFT) {
            throw new TimesheetConflictException(
                    "Only a draft timesheet can be " + verb + "; this one is " + sheet.getStatus());
        }
    }

    private static TimesheetConflictException weekTaken(LocalDate week) {
        return new TimesheetConflictException("You already have a timesheet for the week starting " + week);
    }

    private static boolean isWeekTaken(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause.getMessage() != null && cause.getMessage().contains(UNIQUE_WEEK);
    }

    /**
     * Every rule of the spec's table: the shape ({@link TimesheetRules}), then what needs the database. All errors are
     * reported together, by field.
     */
    private void validate(UUID tenantId, UUID employeeId, TimesheetRequest request) {
        Map<String, String> errors = new LinkedHashMap<>(TimesheetRules.validate(request));
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

    /** Builds the nested lines of a request onto a timesheet. Hours are held at scale 2, as the column is. */
    private static void applyLines(Timesheet sheet, TimesheetRequest request, String actor) {
        for (ProjectLine project : request.projects()) {
            TimesheetProjectEntry projectEntry = sheet.addProject(project.projectId(), actor);
            for (TaskLine task : project.tasks()) {
                TimesheetTaskEntry taskEntry = projectEntry.addTask(task.taskId(), actor);
                for (DayLine day : task.days()) {
                    BigDecimal hours = day.hours().setScale(2);
                    taskEntry.addDay(day.date(), hours, day.description(), actor);
                }
            }
        }
    }

    /** The audit actor: the authenticated caller's name, or {@code system}. */
    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return TimesheetRow.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
