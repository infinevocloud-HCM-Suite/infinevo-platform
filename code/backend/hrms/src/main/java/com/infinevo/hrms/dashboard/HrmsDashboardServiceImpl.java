package com.infinevo.hrms.dashboard;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.hrms.attendance.ClockService;
import com.infinevo.hrms.attendance.ClockSessionResponse;
import com.infinevo.hrms.attendance.TodayResponse;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.DueCounts;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.ProjectRow;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.TaskLoad;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.WaitingRow;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.WeekRow;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.Approvals;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.LateReport;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.ManagedProject;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.Me;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.MyProjects;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.MyTasks;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.ProjectItem;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.Reports;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.TaskItem;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.Team;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.TeamProjects;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.Timesheets;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.Today;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.Waiting;
import com.infinevo.hrms.dashboard.HrmsDashboardResponse.Week;
import com.infinevo.hrms.project.ProjectAccessResolver;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.hrms.project.TaskStatus;
import com.infinevo.hrms.timesheet.TimesheetAccessResolver;
import com.infinevo.hrms.timesheet.TimesheetRules;
import com.infinevo.hrms.timesheet.TimesheetStatus;
import com.infinevo.hrms.timesheet.reminder.LateEmployee;
import com.infinevo.hrms.timesheet.reminder.TimesheetLateQuery;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * W-44 §3. One read for the caller, shaped by the actions they hold rather than by the six legacy role dashboards
 * ({@code legacy/HRMS_Frontend/src/App.jsx:80-97}): a manager is also an employee and sees both {@code me} and
 * {@code team}. Replaces the manager view's whole lists ({@code ManagerDashboardController.java:45-91}) with figures
 * and at most five rows, and counts only {@code SUBMITTED} entries as waiting, where legacy counted drafts
 * ({@code :86-88}).
 */
@Service
@Transactional(readOnly = true)
public class HrmsDashboardServiceImpl implements HrmsDashboardService {

    static final String ACTION_ATTENDANCE_MARK = "hrms.attendance.mark";

    private static final int LIMIT = DashboardQueryRepository.LIMIT;

    /** The open statuses {@code me.tasks.by_status} always carries. */
    private static final List<TaskStatus> OPEN_TASK_STATUSES =
            List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW);

    private final DashboardQueryRepository queries;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;
    private final TenantClock tenantClock;
    private final ClockService clockService;
    private final TimesheetAccessResolver timesheetAccess;
    private final TimesheetLateQuery lateQuery;

    public HrmsDashboardServiceImpl(
            DashboardQueryRepository queries,
            EmployeeService employeeService,
            PermissionService permissionService,
            TenantClock tenantClock,
            ClockService clockService,
            TimesheetAccessResolver timesheetAccess,
            TimesheetLateQuery lateQuery) {
        this.queries = Objects.requireNonNull(queries, "queries must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.clockService = Objects.requireNonNull(clockService, "clockService must not be null");
        this.timesheetAccess = Objects.requireNonNull(timesheetAccess, "timesheetAccess must not be null");
        this.lateQuery = Objects.requireNonNull(lateQuery, "lateQuery must not be null");
    }

    @Override
    public HrmsDashboardResponse forCaller() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse caller = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));
        UUID employeeId = caller.id();
        LocalDate today = tenantClock.today();
        LocalDate week = TimesheetRules.mondayOf(today);

        boolean projectsOwn = permissionService.holds(ProjectAccessResolver.ACTION_READ_OWN);
        Me me = new Me(
                permissionService.holds(ACTION_ATTENDANCE_MARK) ? today() : null,
                permissionService.holds(TimesheetAccessResolver.ACTION_READ_OWN)
                        ? timesheets(tenantId, employeeId, week)
                        : null,
                projectsOwn ? myProjects(tenantId, employeeId) : null,
                projectsOwn ? myTasks(tenantId, employeeId, today, week.plusDays(6)) : null);

        boolean projectsTeam = permissionService.holds(ProjectAccessResolver.ACTION_READ_TEAM);
        boolean approve = permissionService.holds(TimesheetAccessResolver.ACTION_APPROVE);
        boolean readTeam = permissionService.holds(TimesheetAccessResolver.ACTION_READ_TEAM);
        Team team = null;
        if (projectsTeam || approve || readTeam) {
            Set<UUID> managed =
                    projectsTeam || approve ? timesheetAccess.managedProjectIds(tenantId, employeeId) : Set.of();
            team = new Team(
                    projectsTeam ? teamProjects(tenantId, managed, today) : null,
                    approve ? approvals(tenantId, managed) : null,
                    readTeam ? reports(tenantId, employeeId, today, week) : null);
        }
        return new HrmsDashboardResponse(today, me, team);
    }

    private Today today() {
        TodayResponse today = clockService.today();
        ClockSessionResponse open = today.openSession();
        return new Today(open != null, open != null ? open.clockInAt() : null, today.workedMinutes());
    }

    private Timesheets timesheets(UUID tenantId, UUID employeeId, LocalDate week) {
        LocalDate lastWeek = week.minusDays(7);
        List<WeekRow> rows = queries.weeks(tenantId, employeeId, List.of(week, lastWeek));
        return new Timesheets(week(week, rows), week(lastWeek, rows));
    }

    /** The week's sheet, or none: a {@code CANCELLED} sheet reads as none (W-44 §3). Hours rounded once, here. */
    static Week week(LocalDate weekStart, List<WeekRow> rows) {
        for (WeekRow row : rows) {
            if (weekStart.equals(row.weekStart()) && row.status() != TimesheetStatus.CANCELLED) {
                return new Week(weekStart, row.timesheetId(), row.status(), hours(row.hours()));
            }
        }
        return new Week(weekStart, null, null, hours(null));
    }

    private MyProjects myProjects(UUID tenantId, UUID employeeId) {
        List<ProjectItem> items = queries.myProjects(tenantId, employeeId).stream()
                .map(p -> new ProjectItem(p.id(), p.name(), p.status(), p.progress(), p.endDate()))
                .toList();
        return new MyProjects(queries.countMyProjects(tenantId, employeeId), items);
    }

    private MyTasks myTasks(UUID tenantId, UUID employeeId, LocalDate today, LocalDate weekEnd) {
        Map<TaskStatus, Long> counts = queries.myOpenTasksByStatus(tenantId, employeeId);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long open = 0;
        for (TaskStatus status : OPEN_TASK_STATUSES) {
            long n = counts.getOrDefault(status, 0L);
            byStatus.put(status.name(), n);
            open += n;
        }
        DueCounts due = queries.myDueCounts(tenantId, employeeId, today, weekEnd);
        List<TaskItem> next = queries.myNextTasks(tenantId, employeeId).stream()
                .map(t -> new TaskItem(
                        t.id(), t.projectId(), t.projectName(), t.title(), t.status(), t.priority(), t.dueDate()))
                .toList();
        return new MyTasks(open, byStatus, due.overdue(), due.dueThisWeek(), next);
    }

    private TeamProjects teamProjects(UUID tenantId, Set<UUID> managed, LocalDate today) {
        Map<ProjectStatus, Long> counts = queries.projectsByStatus(tenantId, managed);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (ProjectStatus status : ProjectStatus.values()) {
            long n = counts.getOrDefault(status, 0L);
            byStatus.put(status.name(), n);
            total += n;
        }
        List<ProjectRow> started = queries.startedProjects(tenantId, managed);
        List<UUID> ids = started.stream().map(ProjectRow::id).toList();
        Map<UUID, Long> sizes = queries.teamSizes(tenantId, ids);
        Map<UUID, TaskLoad> loads = queries.taskLoads(tenantId, ids, today);
        List<ManagedProject> items = started.stream()
                .map(p -> {
                    TaskLoad load = loads.getOrDefault(p.id(), new TaskLoad(0, 0));
                    return new ManagedProject(
                            p.id(),
                            p.name(),
                            p.progress(),
                            p.endDate(),
                            sizes.getOrDefault(p.id(), 0L),
                            load.open(),
                            load.overdue());
                })
                .toList();
        return new TeamProjects(total, byStatus, items);
    }

    private Approvals approvals(UUID tenantId, Set<UUID> managed) {
        List<Waiting> oldest = queries.oldestWaiting(tenantId, managed).stream()
                .map(HrmsDashboardServiceImpl::waiting)
                .toList();
        return new Approvals(queries.countWaiting(tenantId, managed), oldest);
    }

    private static Waiting waiting(WaitingRow row) {
        return new Waiting(
                row.timesheetId(),
                row.projectEntryId(),
                row.employeeId(),
                displayName(row.firstName(), row.lastName()),
                row.projectName(),
                row.weekStart(),
                row.submittedAt());
    }

    /**
     * Direct reports today; late for last week by {@link TimesheetLateQuery}'s rule, so the dashboard and the reminder
     * mail never disagree (W-44 §13 decision 4).
     */
    private Reports reports(UUID tenantId, UUID employeeId, LocalDate today, LocalDate week) {
        Set<UUID> reportIds = timesheetAccess.directReportIds(tenantId, employeeId);
        if (reportIds.isEmpty()) {
            return new Reports(0, 0, 0, List.of());
        }
        long clockedIn = queries.countClockedIn(tenantId, reportIds, today);
        List<LateEmployee> late = lateQuery.lateEmployees(tenantId, week.minusDays(7)).stream()
                .filter(e -> reportIds.contains(e.employeeId()))
                .toList();
        List<LateReport> first = late.stream()
                .limit(LIMIT)
                .map(e -> new LateReport(e.employeeId(), e.name()))
                .toList();
        return new Reports(reportIds.size(), clockedIn, late.size(), first);
    }

    /** Scale 2, half up — the one rounding, at the response boundary (CONVENTIONS.md §2). */
    static BigDecimal hours(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** As {@code TimesheetLateQueryImpl} names an employee. */
    static String displayName(String first, String last) {
        String name = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        return name.isEmpty() ? "Employee" : name;
    }
}
