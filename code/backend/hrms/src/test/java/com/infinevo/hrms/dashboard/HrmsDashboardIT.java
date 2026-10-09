package com.infinevo.hrms.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-44 §7 integration, against real Postgres as {@code app_user}: the acceptance scenario, a second tenant's rows
 * that never count, a project the manager does not manage, and the two {@code 403}s.
 *
 * <p>Fixture rows are written straight into the tables as the schema owner, so each figure is known exactly. The
 * clock is fixed at Wednesday 2026-10-07 in the tenant's zone (Asia/Kolkata): the week starts 2026-10-05 and last
 * week 2026-09-28.
 *
 * <ul>
 *   <li>Project {@code P}, managed by {@code manager}, has members {@code m1} and {@code m2}. Project {@code X}, in
 *       the same tenant, is managed by {@code otherManager}.
 *   <li>{@code m1}'s week of 2026-09-28 is {@code SUBMITTED}: an entry on P (8.00 + 0.50 hours) and one on X, both
 *       {@code SUBMITTED}. {@code m2}'s week is a {@code DRAFT} with a {@code DRAFT} entry on P.
 *   <li>On P, {@code m1} has a {@code TODO} task due 2026-10-01 (overdue) and an {@code IN_PROGRESS} task due
 *       2026-10-09; a completed task and a deleted overdue task do not count.
 *   <li>{@code m1} is clocked in today; {@code m2}'s only session today is voided.
 *   <li>A second tenant holds a project managed by {@code manager}'s id, with {@code m1} assigned, an overdue task, a
 *       submitted entry and a clock session for {@code m2} — every row with the same employee ids. None of it counts.
 * </ul>
 */
@SpringBootTest(classes = HrmsTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class HrmsDashboardIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-07T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final LocalDate LAST_WEEK = LocalDate.of(2026, 9, 28);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReportingLineRepository reportingLines;

    private UUID tenant;
    private UUID otherTenant;
    private UUID manager;
    private UUID otherManager;
    private UUID m1;
    private UUID m2;
    private UUID projectP;
    private UUID projectX;
    private UUID m1Sheet;
    private UUID m1EntryP;
    private UUID subManager;
    private UUID subMember;
    private UUID subNone;

    @BeforeEach
    void setUp() throws SQLException {
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(NOW, ZoneOffset.UTC));
        tenant = HrmsProjectTestSchema.insertTenant("Dashboard A " + UUID.randomUUID());
        otherTenant = HrmsProjectTestSchema.insertTenant("Dashboard B " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.HRMS));
        HrmsTestApp.ENTITLED.put(otherTenant, Set.of(PlatformModule.HRMS));

        manager = HrmsProjectTestSchema.insertEmployee(tenant, "MGR-" + UUID.randomUUID());
        otherManager = HrmsProjectTestSchema.insertEmployee(tenant, "MGR2-" + UUID.randomUUID());
        m1 = HrmsProjectTestSchema.insertEmployee(tenant, "M1-" + UUID.randomUUID());
        m2 = HrmsProjectTestSchema.insertEmployee(tenant, "M2-" + UUID.randomUUID());

        subManager = UUID.randomUUID();
        subMember = UUID.randomUUID();
        subNone = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(
                tenant,
                subManager,
                "hrms.attendance.mark",
                "hrms.project.read_own",
                "hrms.timesheet.read_own",
                "hrms.project.read_team",
                "hrms.timesheet.read_team",
                "hrms.timesheet.approve");
        HrmsProjectTestSchema.insertMemberWithActions(
                tenant, subMember, "hrms.attendance.mark", "hrms.project.read_own", "hrms.timesheet.read_own");
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subNone, "hrms.attendance.mark");

        projectP = project(tenant, "Project P", manager);
        projectX = project(tenant, "Project X", otherManager);
        assign(tenant, projectP, m1);
        assign(tenant, projectP, m2);

        UUID overdue = task(tenant, projectP, m1, "Overdue", "TODO", LocalDate.of(2026, 10, 1), false);
        task(tenant, projectP, m1, "This week", "IN_PROGRESS", LocalDate.of(2026, 10, 9), false);
        task(tenant, projectP, m1, "Done", "COMPLETED", LocalDate.of(2026, 9, 1), false);
        task(tenant, projectP, m1, "Deleted", "TODO", LocalDate.of(2026, 9, 1), true);

        m1Sheet = sheet(tenant, m1, LAST_WEEK, "SUBMITTED", Instant.parse("2026-10-01T10:00:00Z"));
        m1EntryP = entry(tenant, m1Sheet, projectP, "SUBMITTED");
        UUID taskEntry = taskEntry(tenant, m1EntryP, overdue);
        day(tenant, taskEntry, LAST_WEEK, "8.00");
        day(tenant, taskEntry, LAST_WEEK.plusDays(1), "0.50");
        entry(tenant, m1Sheet, projectX, "SUBMITTED");
        UUID m2Sheet = sheet(tenant, m2, LAST_WEEK, "DRAFT", null);
        entry(tenant, m2Sheet, projectP, "DRAFT");

        clockSession(tenant, m1, TODAY, Instant.parse("2026-10-07T04:00:00Z"), false);
        clockSession(tenant, m2, TODAY, Instant.parse("2026-10-07T04:30:00Z"), true);

        // The second tenant: same employee ids, other tenant_id. None of these rows may count in the first.
        UUID projectB = project(otherTenant, "Project B", manager);
        assign(otherTenant, projectB, m1);
        task(otherTenant, projectB, m1, "B overdue", "TODO", LocalDate.of(2026, 10, 1), false);
        UUID sheetB = sheet(otherTenant, m2, LAST_WEEK, "SUBMITTED", Instant.parse("2026-09-30T10:00:00Z"));
        entry(otherTenant, sheetB, projectB, "SUBMITTED");
        clockSession(otherTenant, m2, TODAY, Instant.parse("2026-10-07T05:00:00Z"), false);

        directReports(manager, m1, m2);
    }

    @AfterEach
    void tearDown() {
        HrmsTestApp.CUSTOM_CLOCK.remove();
        HrmsTestApp.ENTITLED.remove(tenant);
        HrmsTestApp.ENTITLED.remove(otherTenant);
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Acceptance: the manager sees one entry waiting (the draft is not), one overdue task, one report in")
    void managerSeesTeamFigures() throws Exception {
        actAs(manager);

        mvc.perform(authed(get("/api/v1/hrms/dashboard"), subManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.as_of").value("2026-10-07"))
                .andExpect(jsonPath("$.data.team.approvals.waiting").value(1))
                .andExpect(jsonPath("$.data.team.approvals.oldest", hasSize(1)))
                .andExpect(
                        jsonPath("$.data.team.approvals.oldest[0].timesheet_id").value(m1Sheet.toString()))
                .andExpect(jsonPath("$.data.team.approvals.oldest[0].project_entry_id")
                        .value(m1EntryP.toString()))
                .andExpect(
                        jsonPath("$.data.team.approvals.oldest[0].employee_id").value(m1.toString()))
                .andExpect(jsonPath("$.data.team.approvals.oldest[0].employee_name")
                        .value("Test"))
                .andExpect(
                        jsonPath("$.data.team.approvals.oldest[0].project_name").value("Project P"))
                .andExpect(
                        jsonPath("$.data.team.approvals.oldest[0].week_start").value("2026-09-28"))
                .andExpect(jsonPath("$.data.team.projects.managed").value(1))
                .andExpect(jsonPath("$.data.team.projects.by_status.STARTED").value(1))
                .andExpect(jsonPath("$.data.team.projects.by_status.COMPLETED").value(0))
                .andExpect(jsonPath("$.data.team.projects.items", hasSize(1)))
                .andExpect(jsonPath("$.data.team.projects.items[0].project_id").value(projectP.toString()))
                .andExpect(jsonPath("$.data.team.projects.items[0].team_size").value(2))
                .andExpect(jsonPath("$.data.team.projects.items[0].open_tasks").value(2))
                .andExpect(
                        jsonPath("$.data.team.projects.items[0].overdue_tasks").value(1))
                .andExpect(jsonPath("$.data.team.reports.reports").value(2))
                .andExpect(jsonPath("$.data.team.reports.clocked_in_today").value(1))
                .andExpect(jsonPath("$.data.team.reports.late_last_week").value(1))
                .andExpect(jsonPath("$.data.team.reports.late[0].employee_id").value(m2.toString()))
                .andExpect(jsonPath("$.data.me.projects.active").value(0))
                .andExpect(jsonPath("$.data.me.today.clocked_in").value(false));
    }

    @Test
    @DisplayName("Acceptance: the member sees their own figures and team is null")
    void memberSeesOwnFiguresAndNoTeam() throws Exception {
        actAs(m1);

        String body = mvc.perform(authed(get("/api/v1/hrms/dashboard"), subMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team").value(nullValue()))
                .andExpect(jsonPath("$.data.me.today.clocked_in").value(true))
                .andExpect(jsonPath("$.data.me.today.clocked_in_at").value("2026-10-07T04:00:00Z"))
                .andExpect(jsonPath("$.data.me.timesheets.this_week.week_start").value("2026-10-05"))
                .andExpect(jsonPath("$.data.me.timesheets.this_week.status").value(nullValue()))
                .andExpect(
                        jsonPath("$.data.me.timesheets.this_week.timesheet_id").value(nullValue()))
                .andExpect(jsonPath("$.data.me.timesheets.last_week.status").value("SUBMITTED"))
                .andExpect(
                        jsonPath("$.data.me.timesheets.last_week.timesheet_id").value(m1Sheet.toString()))
                .andExpect(jsonPath("$.data.me.projects.active").value(1))
                .andExpect(jsonPath("$.data.me.projects.items[0].project_id").value(projectP.toString()))
                .andExpect(jsonPath("$.data.me.tasks.open").value(2))
                .andExpect(jsonPath("$.data.me.tasks.by_status.TODO").value(1))
                .andExpect(jsonPath("$.data.me.tasks.by_status.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.data.me.tasks.by_status.IN_REVIEW").value(0))
                .andExpect(jsonPath("$.data.me.tasks.overdue").value(1))
                .andExpect(jsonPath("$.data.me.tasks.due_this_week").value(1))
                .andExpect(jsonPath("$.data.me.tasks.next", hasSize(2)))
                .andExpect(jsonPath("$.data.me.tasks.next[0].title").value("Overdue"))
                .andExpect(jsonPath("$.data.me.tasks.next[0].project_name").value("Project P"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .as("hours is the day entries' sum at scale 2")
                .contains("\"hours\":8.50")
                .contains("\"hours\":0.00");
    }

    @Test
    @DisplayName("The second tenant's project, task, timesheet and clock rows never count; its manager sees only them")
    void secondTenantNeverCounts() throws Exception {
        UUID subB = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(
                otherTenant, subB, "hrms.project.read_team", "hrms.timesheet.approve", "hrms.timesheet.read_team");
        actAs(manager);

        // In tenant B the same manager id manages only Project B, with its one submitted entry; no reports there.
        mvc.perform(authedIn(otherTenant, get("/api/v1/hrms/dashboard"), subB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team.projects.managed").value(1))
                .andExpect(jsonPath("$.data.team.projects.items[0].name").value("Project B"))
                .andExpect(jsonPath("$.data.team.projects.items[0].team_size").value(1))
                .andExpect(
                        jsonPath("$.data.team.projects.items[0].overdue_tasks").value(1))
                .andExpect(jsonPath("$.data.team.approvals.waiting").value(1))
                .andExpect(jsonPath("$.data.team.reports.reports").value(0));

        // And tenant A's figures are as the acceptance test has them: B's rows add nothing.
        mvc.perform(authed(get("/api/v1/hrms/dashboard"), subManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team.approvals.waiting").value(1))
                .andExpect(
                        jsonPath("$.data.team.projects.items[0].overdue_tasks").value(1))
                .andExpect(jsonPath("$.data.team.reports.clocked_in_today").value(1));
    }

    @Test
    @DisplayName("A manager does not see a project they do not manage, nor its submitted entries")
    void unmanagedProjectIsNotSeen() throws Exception {
        actAs(otherManager);

        mvc.perform(authed(get("/api/v1/hrms/dashboard"), subManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team.projects.managed").value(1))
                .andExpect(jsonPath("$.data.team.projects.items[0].project_id").value(projectX.toString()))
                .andExpect(jsonPath("$.data.team.projects.items[0].team_size").value(0))
                .andExpect(jsonPath("$.data.team.approvals.waiting").value(1))
                .andExpect(
                        jsonPath("$.data.team.approvals.oldest[0].project_name").value("Project X"))
                .andExpect(jsonPath("$.data.team.reports.reports").value(0));
    }

    @Test
    @DisplayName("HRMS module off is 403 MODULE_NOT_ENTITLED")
    void moduleOffIsForbidden() throws Exception {
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.PAYROLL));
        actAs(manager);

        mvc.perform(authed(get("/api/v1/hrms/dashboard"), subManager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    @Test
    @DisplayName("A login holding none of the five actions is 403 FORBIDDEN")
    void noneOfTheFiveActionsIsForbidden() throws Exception {
        actAs(m2);

        mvc.perform(authed(get("/api/v1/hrms/dashboard"), subNone))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A login with no employee record is 403, never 500")
    void noEmployeeIsForbidden() throws Exception {
        HrmsTestApp.CURRENT_EMPLOYEE.remove();

        mvc.perform(authed(get("/api/v1/hrms/dashboard"), subMember)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-76: a login that approves timesheets but has no employee record gets 200 and the team blocks only")
    void approverWithoutEmployeeGetsTeamBlocksOnly() throws Exception {
        UUID subApprover = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subApprover, "hrms.timesheet.approve");
        HrmsTestApp.CURRENT_EMPLOYEE.remove();

        mvc.perform(authed(get("/api/v1/hrms/dashboard"), subApprover))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.me").value(nullValue()))
                .andExpect(jsonPath("$.data.team.approvals.waiting").value(0))
                .andExpect(jsonPath("$.data.team.projects").value(nullValue()))
                .andExpect(jsonPath("$.data.team.reports").value(nullValue()));
    }

    // --- fixture -------------------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return authedIn(tenant, builder, sub);
    }

    private static MockHttpServletRequestBuilder authedIn(
            UUID tenantId, MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenantId.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test")));
    }

    private void actAs(UUID employee) {
        HrmsTestApp.CURRENT_EMPLOYEE.set(new EmployeeResponse(
                employee,
                tenant,
                "E-" + employee.toString().substring(0, 6),
                "Test",
                null,
                null,
                "MALE",
                LocalDate.of(2026, 4, 1),
                null,
                EmploymentStatus.ACTIVE,
                employee + "@hrms.test",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()));
    }

    /** Stubs core's reporting lines, which this context does not load, as {@code TimesheetReviewSupport} does. */
    private void directReports(UUID managerId, UUID... employees) {
        List<ReportingLine> lines = Arrays.stream(employees)
                .map(id -> {
                    Employee employee = mock(Employee.class);
                    when(employee.getId()).thenReturn(id);
                    ReportingLine line = mock(ReportingLine.class);
                    when(line.getEmployee()).thenReturn(employee);
                    return line;
                })
                .toList();
        when(reportingLines.findDirectReports(eq(tenant), eq(managerId), any())).thenReturn(lines);
    }

    private static UUID project(UUID tenantId, String name, UUID managerId) throws SQLException {
        return HrmsProjectTestSchema.uuid(
                "INSERT INTO hrms.project (tenant_id, name, start_date, priority, status, manager_employee_id)"
                        + " VALUES (?, ?, DATE '2026-01-01', 'MEDIUM', 'STARTED', ?) RETURNING id",
                tenantId,
                name,
                managerId);
    }

    private static void assign(UUID tenantId, UUID projectId, UUID employeeId) throws SQLException {
        HrmsProjectTestSchema.update(
                "INSERT INTO hrms.assignment (tenant_id, project_id, employee_id, assigned_on)"
                        + " VALUES (?, ?, ?, DATE '2026-01-01')",
                tenantId,
                projectId,
                employeeId);
    }

    private static UUID task(
            UUID tenantId, UUID projectId, UUID assignee, String title, String status, LocalDate due, boolean deleted)
            throws SQLException {
        return HrmsProjectTestSchema.uuid(
                "INSERT INTO hrms.task (tenant_id, project_id, title, assignee_employee_id, due_date, priority,"
                        + " status, is_deleted) VALUES (?, ?, ?, ?, ?, 'MEDIUM', ?, ?) RETURNING id",
                tenantId,
                projectId,
                title,
                assignee,
                due,
                status,
                deleted);
    }

    private static UUID sheet(UUID tenantId, UUID employeeId, LocalDate week, String status, Instant submittedAt)
            throws SQLException {
        return HrmsProjectTestSchema.uuid(
                "INSERT INTO hrms.timesheet (tenant_id, employee_id, week_start_date, week_end_date, status,"
                        + " submitted_at) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                tenantId,
                employeeId,
                week,
                week.plusDays(6),
                status,
                submittedAt == null ? null : Timestamp.from(submittedAt));
    }

    private static UUID entry(UUID tenantId, UUID sheetId, UUID projectId, String status) throws SQLException {
        return HrmsProjectTestSchema.uuid(
                "INSERT INTO hrms.timesheet_project_entry (tenant_id, timesheet_id, project_id, status)"
                        + " VALUES (?, ?, ?, ?) RETURNING id",
                tenantId,
                sheetId,
                projectId,
                status);
    }

    private static UUID taskEntry(UUID tenantId, UUID entryId, UUID taskId) throws SQLException {
        return HrmsProjectTestSchema.uuid(
                "INSERT INTO hrms.timesheet_task_entry (tenant_id, project_entry_id, task_id) VALUES (?, ?, ?)"
                        + " RETURNING id",
                tenantId,
                entryId,
                taskId);
    }

    private static void day(UUID tenantId, UUID taskEntryId, LocalDate date, String hours) throws SQLException {
        HrmsProjectTestSchema.update(
                "INSERT INTO hrms.timesheet_day_entry (tenant_id, task_entry_id, work_date, hours)"
                        + " VALUES (?, ?, ?, ?)",
                tenantId,
                taskEntryId,
                date,
                new java.math.BigDecimal(hours));
    }

    private static void clockSession(UUID tenantId, UUID employeeId, LocalDate date, Instant in, boolean voided)
            throws SQLException {
        HrmsProjectTestSchema.update(
                "INSERT INTO hrms.clock_session (tenant_id, employee_id, attendance_date, clock_in_at, voided_at,"
                        + " void_reason) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId,
                employeeId,
                date,
                Timestamp.from(in),
                voided ? Timestamp.from(in.plusSeconds(60)) : null,
                voided ? "NOT_CLOCKED_OUT" : null);
    }
}
