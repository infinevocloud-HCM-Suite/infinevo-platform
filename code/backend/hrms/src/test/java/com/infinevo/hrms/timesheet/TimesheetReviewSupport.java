package com.infinevo.hrms.timesheet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.hrms.project.AssignmentRequest;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.Priority;
import com.infinevo.hrms.project.ProjectRequest;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared set-up for the W-42.4 review tests, on top of {@link TimesheetItSupport}.
 *
 * <p>Two projects, {@code A} managed by {@code m1} and {@code B} by {@code m2}. Employee {@code empA} is assigned to
 * both and has four weeks, each with a line on A and a line on B: one submitted, one approved, one with A approved and
 * B rejected, and one draft. Employee {@code empB} is assigned to B only and has one submitted week, with a line on B.
 * {@code reporter} is {@code empA}'s reporting manager and has no other direct report. Statuses are written straight
 * into the rows, as the spec says: this does not wait on submit and approve.
 *
 * <p>The tokens: {@code subM1}, {@code subM2} (approve), {@code subReporter} (read_team), {@code subHr} (read), and
 * {@code subA} (the employee, from the base).
 */
abstract class TimesheetReviewSupport extends TimesheetItSupport {

    /** Four Mondays, newest last: October 2026. */
    protected static final LocalDate W1 = LocalDate.of(2026, 10, 5);

    protected static final LocalDate W2 = W1.plusDays(7);
    protected static final LocalDate W3 = W1.plusDays(14);
    protected static final LocalDate W4 = W1.plusDays(21);

    @Autowired
    protected ReportingLineRepository reportingLines;

    protected UUID m1;
    protected UUID m2;
    protected UUID reporter;
    protected UUID hrEmp;
    protected UUID projectA;
    protected UUID projectB;
    protected UUID taskA;
    protected UUID taskB;
    protected UUID subM1;
    protected UUID subM2;
    protected UUID subReporter;
    protected UUID subHr;

    protected UUID sheetW1;
    protected UUID sheetW2;
    protected UUID sheetW3;
    protected UUID sheetW4;
    protected UUID sheetOfB;

    @BeforeEach
    void seedReview() throws Exception {
        m1 = HrmsProjectTestSchema.insertEmployee(tenant, "M1-" + UUID.randomUUID());
        m2 = HrmsProjectTestSchema.insertEmployee(tenant, "M2-" + UUID.randomUUID());
        reporter = HrmsProjectTestSchema.insertEmployee(tenant, "RPT-" + UUID.randomUUID());
        hrEmp = HrmsProjectTestSchema.insertEmployee(tenant, "HR-" + UUID.randomUUID());
        subM1 = UUID.randomUUID();
        subM2 = UUID.randomUUID();
        subReporter = UUID.randomUUID();
        subHr = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subM1, "hrms.timesheet.approve");
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subM2, "hrms.timesheet.approve");
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subReporter, "hrms.timesheet.read_team");
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subHr, "hrms.timesheet.read");

        TenantContext.set(tenant);
        try {
            projectA = managedProject("Review A " + UUID.randomUUID(), m1);
            projectB = managedProject("Review B " + UUID.randomUUID(), m2);
            taskA = task(projectA, "Task A");
            taskB = task(projectB, "Task B");
            assignments.assign(projectA, new AssignmentRequest(empA, LocalDate.of(2026, 1, 1)));
            assignments.assign(projectB, new AssignmentRequest(empA, LocalDate.of(2026, 1, 1)));
            assignments.assign(projectB, new AssignmentRequest(empB, LocalDate.of(2026, 1, 1)));

            actAs(empA);
            sheetW1 = twoProjectWeek(W1);
            sheetW2 = twoProjectWeek(W2);
            sheetW3 = twoProjectWeek(W3);
            sheetW4 = twoProjectWeek(W4);
            actAs(empB);
            sheetOfB = timesheets
                    .create(request(W1, projectB, taskB, day(W1, "5")))
                    .id();
        } finally {
            TenantContext.clear();
        }

        setStatus(sheetW1, TimesheetStatus.SUBMITTED, TimesheetStatus.SUBMITTED, TimesheetStatus.SUBMITTED);
        setStatus(sheetW2, TimesheetStatus.APPROVED, TimesheetStatus.APPROVED, TimesheetStatus.APPROVED);
        setStatus(sheetW3, TimesheetStatus.REJECTED, TimesheetStatus.APPROVED, TimesheetStatus.REJECTED);
        setStatus(sheetOfB, TimesheetStatus.SUBMITTED, TimesheetStatus.SUBMITTED, TimesheetStatus.SUBMITTED);

        reset(reportingLines);
        directReports(reporter, empA);
        actAs(empA);
    }

    @AfterEach
    void forgetReports() {
        reset(reportingLines);
    }

    private UUID managedProject(String name, UUID manager) {
        return projects.create(new ProjectRequest(
                        name,
                        "Dev",
                        "Review test",
                        LocalDate.of(2026, 1, 1),
                        null,
                        Priority.MEDIUM,
                        ProjectStatus.STARTED,
                        BigDecimal.valueOf(1000),
                        manager))
                .id();
    }

    /** A draft with a line on A and a line on B, as the logged-in employee. */
    private UUID twoProjectWeek(LocalDate week) {
        return timesheets
                .create(new TimesheetRequest(
                        week,
                        List.of(
                                new TimesheetRequest.ProjectLine(
                                        projectA,
                                        List.of(new TimesheetRequest.TaskLine(taskA, List.of(day(week, "4"))))),
                                new TimesheetRequest.ProjectLine(
                                        projectB,
                                        List.of(new TimesheetRequest.TaskLine(taskB, List.of(day(week, "3"))))))))
                .id();
    }

    /** Writes a week's status and the status of its A and B lines directly, as submit and approve will later. */
    protected void setStatus(UUID sheet, TimesheetStatus week, TimesheetStatus lineA, TimesheetStatus lineB)
            throws SQLException {
        HrmsProjectTestSchema.update("UPDATE hrms.timesheet SET status = ? WHERE id = ?", week.name(), sheet);
        HrmsProjectTestSchema.update(
                "UPDATE hrms.timesheet_project_entry SET status = ? WHERE timesheet_id = ? AND project_id = ?",
                lineA.name(),
                sheet,
                projectA);
        HrmsProjectTestSchema.update(
                "UPDATE hrms.timesheet_project_entry SET status = ? WHERE timesheet_id = ? AND project_id = ?",
                lineB.name(),
                sheet,
                projectB);
    }

    /** Says who is {@code manager}'s direct reports, as core's reporting lines would. */
    protected void directReports(UUID manager, UUID... employees) {
        List<ReportingLine> lines = java.util.Arrays.stream(employees)
                .map(id -> {
                    Employee employee = mock(Employee.class);
                    when(employee.getId()).thenReturn(id);
                    ReportingLine line = mock(ReportingLine.class);
                    when(line.getEmployee()).thenReturn(employee);
                    return line;
                })
                .toList();
        when(reportingLines.findDirectReports(eq(tenant), eq(manager), any())).thenReturn(lines);
    }
}
