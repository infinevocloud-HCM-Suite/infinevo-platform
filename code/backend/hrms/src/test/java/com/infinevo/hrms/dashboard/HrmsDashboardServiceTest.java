package com.infinevo.hrms.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.hrms.attendance.ClockService;
import com.infinevo.hrms.attendance.ClockSessionResponse;
import com.infinevo.hrms.attendance.SessionOrigin;
import com.infinevo.hrms.attendance.TodayResponse;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.DueCounts;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.ProjectRow;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.TaskLoad;
import com.infinevo.hrms.dashboard.DashboardQueryRepository.WeekRow;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.hrms.project.TaskStatus;
import com.infinevo.hrms.timesheet.TimesheetAccessResolver;
import com.infinevo.hrms.timesheet.TimesheetStatus;
import com.infinevo.hrms.timesheet.reminder.LateEmployee;
import com.infinevo.hrms.timesheet.reminder.TimesheetLateQuery;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** W-44 §7 unit: which blocks a caller sees, the 403 without an employee, and a cancelled week reading as none. */
class HrmsDashboardServiceTest {

    private static final String MARK = "hrms.attendance.mark";
    private static final String TS_OWN = "hrms.timesheet.read_own";
    private static final String PROJECT_OWN = "hrms.project.read_own";
    private static final String PROJECT_TEAM = "hrms.project.read_team";
    private static final String APPROVE = "hrms.timesheet.approve";
    private static final String TS_TEAM = "hrms.timesheet.read_team";
    private static final Set<String> ALL = Set.of(MARK, TS_OWN, PROJECT_OWN, PROJECT_TEAM, APPROVE, TS_TEAM);

    /** A Wednesday: the week starts 2026-10-05, last week 2026-09-28. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 5);
    private static final LocalDate LAST_WEEK = LocalDate.of(2026, 9, 28);

    private final UUID tenant = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();
    private final UUID managedProject = UUID.randomUUID();
    private final UUID report = UUID.randomUUID();
    private final Set<String> held = new HashSet<>();

    private DashboardQueryRepository queries;
    private EmployeeService employees;
    private ClockService clock;
    private TimesheetAccessResolver access;
    private TimesheetLateQuery lateQuery;
    private HrmsDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenant);
        queries = mock(DashboardQueryRepository.class);
        employees = mock(EmployeeService.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(any())).thenAnswer(i -> held.contains(i.<String>getArgument(0)));
        TenantClock tenantClock = mock(TenantClock.class);
        when(tenantClock.today()).thenReturn(TODAY);
        clock = mock(ClockService.class);
        access = mock(TimesheetAccessResolver.class);
        lateQuery = mock(TimesheetLateQuery.class);
        service = new HrmsDashboardServiceImpl(queries, employees, permissions, tenantClock, clock, access, lateQuery);

        when(employees.currentEmployee()).thenReturn(Optional.of(employee(me)));
        Instant in = Instant.parse("2026-10-07T03:30:00Z");
        when(clock.today())
                .thenReturn(new TodayResponse(
                        TODAY,
                        new ClockSessionResponse(
                                UUID.randomUUID(), me, null, TODAY, in, null, null, SessionOrigin.CLOCK, null, null),
                        List.of(),
                        95));
        when(queries.weeks(eq(tenant), eq(me), anyCollection()))
                .thenReturn(
                        List.of(new WeekRow(UUID.randomUUID(), WEEK, TimesheetStatus.DRAFT, new BigDecimal("7.5"))));
        when(queries.countMyProjects(tenant, me)).thenReturn(1L);
        when(queries.myProjects(tenant, me))
                .thenReturn(List.of(new ProjectRow(managedProject, "P", ProjectStatus.STARTED, 40, null)));
        when(queries.myOpenTasksByStatus(tenant, me)).thenReturn(Map.of(TaskStatus.IN_PROGRESS, 2L));
        when(queries.myDueCounts(tenant, me, TODAY, LocalDate.of(2026, 10, 11))).thenReturn(new DueCounts(1, 1));
        when(queries.myNextTasks(tenant, me)).thenReturn(List.of());
        when(access.managedProjectIds(tenant, me)).thenReturn(Set.of(managedProject));
        when(queries.projectsByStatus(tenant, Set.of(managedProject))).thenReturn(Map.of(ProjectStatus.STARTED, 1L));
        when(queries.startedProjects(tenant, Set.of(managedProject)))
                .thenReturn(List.of(new ProjectRow(managedProject, "P", ProjectStatus.STARTED, 40, null)));
        when(queries.teamSizes(tenant, List.of(managedProject))).thenReturn(Map.of(managedProject, 2L));
        when(queries.taskLoads(tenant, List.of(managedProject), TODAY))
                .thenReturn(Map.of(managedProject, new TaskLoad(3, 1)));
        when(queries.countWaiting(tenant, Set.of(managedProject))).thenReturn(1L);
        when(queries.oldestWaiting(tenant, Set.of(managedProject))).thenReturn(List.of());
        when(access.directReportIds(tenant, me)).thenReturn(Set.of(report));
        when(queries.countClockedIn(tenant, Set.of(report), TODAY)).thenReturn(1L);
        when(lateQuery.lateEmployees(tenant, LAST_WEEK)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Holding every action, every block is present and figured")
    void everyBlockWithEveryAction() {
        held.addAll(ALL);

        HrmsDashboardResponse r = service.forCaller();

        assertThat(r.asOf()).isEqualTo(TODAY);
        assertThat(r.me().today().clockedIn()).isTrue();
        assertThat(r.me().today().clockedInAt()).isEqualTo(Instant.parse("2026-10-07T03:30:00Z"));
        assertThat(r.me().today().workedMinutes()).isEqualTo(95);
        assertThat(r.me().timesheets().thisWeek().status()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(r.me().timesheets().thisWeek().hours()).isEqualTo(new BigDecimal("7.50"));
        assertThat(r.me().timesheets().lastWeek().weekStart()).isEqualTo(LAST_WEEK);
        assertThat(r.me().timesheets().lastWeek().status()).isNull();
        assertThat(r.me().timesheets().lastWeek().hours()).isEqualTo(new BigDecimal("0.00"));
        assertThat(r.me().projects().active()).isEqualTo(1);
        assertThat(r.me().tasks().byStatus())
                .containsExactly(Map.entry("TODO", 0L), Map.entry("IN_PROGRESS", 2L), Map.entry("IN_REVIEW", 0L));
        assertThat(r.me().tasks().open()).isEqualTo(2);
        assertThat(r.me().tasks().overdue()).isEqualTo(1);
        assertThat(r.team().projects().managed()).isEqualTo(1);
        assertThat(r.team().projects().byStatus())
                .containsExactly(Map.entry("STARTED", 1L), Map.entry("COMPLETED", 0L));
        assertThat(r.team().projects().items().get(0).teamSize()).isEqualTo(2);
        assertThat(r.team().projects().items().get(0).openTasks()).isEqualTo(3);
        assertThat(r.team().projects().items().get(0).overdueTasks()).isEqualTo(1);
        assertThat(r.team().approvals().waiting()).isEqualTo(1);
        assertThat(r.team().reports().reports()).isEqualTo(1);
        assertThat(r.team().reports().clockedInToday()).isEqualTo(1);
    }

    @Test
    @DisplayName("Each block is null without its action, and its data is never read")
    void eachBlockNullWithoutItsAction() {
        assertThat(without(MARK).me().today()).isNull();
        verifyNoInteractions(clock);

        assertThat(without(TS_OWN).me().timesheets()).isNull();
        verify(queries, never()).weeks(any(), any(), anyCollection());

        HrmsDashboardResponse noOwnProjects = without(PROJECT_OWN);
        assertThat(noOwnProjects.me().projects()).isNull();
        assertThat(noOwnProjects.me().tasks()).isNull();
        verify(queries, never()).myProjects(any(), any());
        verify(queries, never()).myNextTasks(any(), any());

        HrmsDashboardResponse noTeamProjects = without(PROJECT_TEAM);
        assertThat(noTeamProjects.team().projects()).isNull();
        assertThat(noTeamProjects.team().approvals()).isNotNull();
        verify(queries, never()).startedProjects(any(), any());

        HrmsDashboardResponse noApprove = without(APPROVE);
        assertThat(noApprove.team().approvals()).isNull();
        assertThat(noApprove.team().projects()).isNotNull();
        verify(queries, never()).countWaiting(any(), any());

        HrmsDashboardResponse noTeamTimesheets = without(TS_TEAM);
        assertThat(noTeamTimesheets.team().reports()).isNull();
        verify(access, never()).directReportIds(any(), any());
        verifyNoInteractions(lateQuery);
    }

    /** The dashboard for a caller holding every action but {@code action}, with earlier calls forgotten. */
    private HrmsDashboardResponse without(String action) {
        clearInvocations(queries, clock, access, lateQuery);
        held.clear();
        held.addAll(ALL);
        held.remove(action);
        return service.forCaller();
    }

    @Test
    @DisplayName("team is null when all three of its blocks are; me stands")
    void teamNullWhenAllThreeAre() {
        held.addAll(Set.of(MARK, TS_OWN, PROJECT_OWN));

        HrmsDashboardResponse r = service.forCaller();

        assertThat(r.team()).isNull();
        assertThat(r.me().today()).isNotNull();
        assertThat(r.me().timesheets()).isNotNull();
        assertThat(r.me().projects()).isNotNull();
        assertThat(r.me().tasks()).isNotNull();
        verify(access, never()).managedProjectIds(any(), any());
    }

    @Test
    @DisplayName("No employee record and only own actions is AccessDeniedException (403), never 500")
    void noEmployeeIsAccessDenied() {
        held.addAll(Set.of(MARK, TS_OWN, PROJECT_OWN));
        when(employees.currentEmployee()).thenReturn(Optional.empty());

        assertThatThrownBy(service::forCaller).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(queries, clock, access, lateQuery);
    }

    @Test
    @DisplayName("D-76: no employee record but a team action is the team blocks only; me is null and never read")
    void noEmployeeWithTeamActionsGetsTeamBlocksOnly() {
        held.addAll(ALL);
        when(employees.currentEmployee()).thenReturn(Optional.empty());

        HrmsDashboardResponse r = service.forCaller();

        assertThat(r.asOf()).isEqualTo(TODAY);
        assertThat(r.me()).isNull();
        assertThat(r.team().approvals().waiting()).isZero();
        assertThat(r.team().approvals().oldest()).isEmpty();
        assertThat(r.team().projects().managed()).isZero();
        assertThat(r.team().reports().reports()).isZero();
        // No employee, so nothing managed and no reports: never a query keyed on a missing id.
        verifyNoInteractions(clock, access, lateQuery);
        verify(queries, never()).weeks(any(), any(), anyCollection());
        verify(queries, never()).myProjects(any(), any());
    }

    @Test
    @DisplayName("D-76: hr's approve action alone, with no employee record, is the approvals block")
    void noEmployeeWithApproveOnly() {
        held.add(APPROVE);
        when(employees.currentEmployee()).thenReturn(Optional.empty());

        HrmsDashboardResponse r = service.forCaller();

        assertThat(r.me()).isNull();
        assertThat(r.team().approvals()).isNotNull();
        assertThat(r.team().projects()).isNull();
        assertThat(r.team().reports()).isNull();
    }

    @Test
    @DisplayName("A CANCELLED week reads as none: status and timesheet_id null, hours 0.00")
    void cancelledWeekReadsAsNone() {
        held.add(TS_OWN);
        when(queries.weeks(eq(tenant), eq(me), anyCollection()))
                .thenReturn(List.of(
                        new WeekRow(UUID.randomUUID(), WEEK, TimesheetStatus.CANCELLED, new BigDecimal("8.00")),
                        new WeekRow(UUID.randomUUID(), LAST_WEEK, TimesheetStatus.SUBMITTED, null)));

        HrmsDashboardResponse r = service.forCaller();

        assertThat(r.me().timesheets().thisWeek().weekStart()).isEqualTo(WEEK);
        assertThat(r.me().timesheets().thisWeek().status()).isNull();
        assertThat(r.me().timesheets().thisWeek().timesheetId()).isNull();
        assertThat(r.me().timesheets().thisWeek().hours()).isEqualTo(new BigDecimal("0.00"));
        assertThat(r.me().timesheets().lastWeek().status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(r.me().timesheets().lastWeek().hours()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Late counts only direct reports, from last week's Monday, and lists at most five")
    void lateIsFilteredToReportsAndCapped() {
        held.add(TS_TEAM);
        List<UUID> reports =
                IntStream.range(0, 7).mapToObj(i -> UUID.randomUUID()).toList();
        when(access.directReportIds(tenant, me)).thenReturn(Set.copyOf(reports));
        List<LateEmployee> late = new java.util.ArrayList<>();
        late.add(new LateEmployee(UUID.randomUUID(), "Not a report", null));
        reports.forEach(id -> late.add(new LateEmployee(id, "Report " + id, me)));
        when(lateQuery.lateEmployees(tenant, LAST_WEEK)).thenReturn(late);

        HrmsDashboardResponse r = service.forCaller();

        assertThat(r.team().projects()).isNull();
        assertThat(r.team().approvals()).isNull();
        assertThat(r.team().reports().reports()).isEqualTo(7);
        assertThat(r.team().reports().lateLastWeek()).isEqualTo(7);
        assertThat(r.team().reports().late())
                .extracting(HrmsDashboardResponse.LateReport::employeeId)
                .containsExactlyElementsOf(reports.subList(0, 5));
        verify(access, never()).managedProjectIds(any(), any());
    }

    @Test
    @DisplayName("A manager with no direct reports gets zeros without a query")
    void noReportsNoQuery() {
        held.add(TS_TEAM);
        when(access.directReportIds(tenant, me)).thenReturn(Set.of());

        HrmsDashboardResponse r = service.forCaller();

        assertThat(r.team().reports().reports()).isZero();
        assertThat(r.team().reports().late()).isEmpty();
        verify(queries, never()).countClockedIn(any(), any(), any());
        verifyNoInteractions(lateQuery);
    }

    private static EmployeeResponse employee(UUID id) {
        return new EmployeeResponse(
                id,
                UUID.randomUUID(),
                "E-1",
                "Test",
                null,
                "Employee",
                "MALE",
                LocalDate.of(2026, 4, 1),
                null,
                EmploymentStatus.ACTIVE,
                "e@test.local",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}
