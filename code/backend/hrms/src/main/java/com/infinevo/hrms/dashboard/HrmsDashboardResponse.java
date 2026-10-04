package com.infinevo.hrms.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.hrms.project.Priority;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.hrms.project.TaskStatus;
import com.infinevo.hrms.timesheet.TimesheetStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The HRMS dashboard for the caller (W-44 §4): figures plus at most five rows per block. A block the caller holds no
 * action for is {@code null} — present in the JSON, never absent and never a {@code 403}. {@code team} is
 * {@code null} when all three of its blocks are.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HrmsDashboardResponse(
        @JsonProperty("as_of") LocalDate asOf, @JsonProperty("me") Me me, @JsonProperty("team") Team team) {

    /** The caller's own figures. Each block is null without its action. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Me(
            @JsonProperty("today") Today today,
            @JsonProperty("timesheets") Timesheets timesheets,
            @JsonProperty("projects") MyProjects projects,
            @JsonProperty("tasks") MyTasks tasks) {}

    /** Today's clock: an open session means clocked in now; minutes count closed, non-voided sessions. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Today(
            @JsonProperty("clocked_in") boolean clockedIn,
            @JsonProperty("clocked_in_at") Instant clockedInAt,
            @JsonProperty("worked_minutes") int workedMinutes) {}

    public record Timesheets(@JsonProperty("this_week") Week thisWeek, @JsonProperty("last_week") Week lastWeek) {}

    /**
     * One week of the caller's timesheet. {@code timesheet_id} and {@code status} are null when there is no sheet or
     * it is {@code CANCELLED}; {@code hours} is the sum of the day entries at scale 2, {@code 0.00} when none.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Week(
            @JsonProperty("week_start") LocalDate weekStart,
            @JsonProperty("timesheet_id") UUID timesheetId,
            @JsonProperty("status") TimesheetStatus status,
            @JsonProperty("hours") BigDecimal hours) {}

    /** Live assignments to live {@code STARTED} projects: the count and the first five by end date. */
    public record MyProjects(@JsonProperty("active") long active, @JsonProperty("items") List<ProjectItem> items) {}

    public record ProjectItem(
            @JsonProperty("project_id") UUID projectId,
            @JsonProperty("name") String name,
            @JsonProperty("status") ProjectStatus status,
            @JsonProperty("progress") int progress,
            @JsonProperty("end_date") LocalDate endDate) {}

    /**
     * Live tasks assigned to the caller on live projects, not {@code COMPLETED}. {@code by_status} always carries
     * {@code TODO}, {@code IN_PROGRESS} and {@code IN_REVIEW}; {@code due_this_week} is due from today to Sunday.
     */
    public record MyTasks(
            @JsonProperty("open") long open,
            @JsonProperty("by_status") Map<String, Long> byStatus,
            @JsonProperty("overdue") long overdue,
            @JsonProperty("due_this_week") long dueThisWeek,
            @JsonProperty("next") List<TaskItem> next) {}

    public record TaskItem(
            @JsonProperty("task_id") UUID taskId,
            @JsonProperty("project_id") UUID projectId,
            @JsonProperty("project_name") String projectName,
            @JsonProperty("title") String title,
            @JsonProperty("status") TaskStatus status,
            @JsonProperty("priority") Priority priority,
            @JsonProperty("due_date") LocalDate dueDate) {}

    /** The manager's figures. Each block is null without its action. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Team(
            @JsonProperty("projects") TeamProjects projects,
            @JsonProperty("approvals") Approvals approvals,
            @JsonProperty("reports") Reports reports) {}

    /** Live projects the caller manages, counted by status; the first five {@code STARTED} by end date. */
    public record TeamProjects(
            @JsonProperty("managed") long managed,
            @JsonProperty("by_status") Map<String, Long> byStatus,
            @JsonProperty("items") List<ManagedProject> items) {}

    public record ManagedProject(
            @JsonProperty("project_id") UUID projectId,
            @JsonProperty("name") String name,
            @JsonProperty("progress") int progress,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("team_size") long teamSize,
            @JsonProperty("open_tasks") long openTasks,
            @JsonProperty("overdue_tasks") long overdueTasks) {}

    /** Project entries {@code SUBMITTED} on projects the caller manages — never a draft — and the oldest five. */
    public record Approvals(@JsonProperty("waiting") long waiting, @JsonProperty("oldest") List<Waiting> oldest) {}

    public record Waiting(
            @JsonProperty("timesheet_id") UUID timesheetId,
            @JsonProperty("project_entry_id") UUID projectEntryId,
            @JsonProperty("employee_id") UUID employeeId,
            @JsonProperty("employee_name") String employeeName,
            @JsonProperty("project_name") String projectName,
            @JsonProperty("week_start") LocalDate weekStart,
            @JsonProperty("submitted_at") Instant submittedAt) {}

    /** The caller's direct reports today; {@code late} holds at most five, in {@code TimesheetLateQuery}'s order. */
    public record Reports(
            @JsonProperty("reports") long reports,
            @JsonProperty("clocked_in_today") long clockedInToday,
            @JsonProperty("late_last_week") long lateLastWeek,
            @JsonProperty("late") List<LateReport> late) {}

    public record LateReport(@JsonProperty("employee_id") UUID employeeId, @JsonProperty("name") String name) {}
}
