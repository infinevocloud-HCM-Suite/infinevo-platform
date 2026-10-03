package com.infinevo.hrms.timesheet;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A timesheet as the API returns it, nested project, task and day (W-42.1 §4).
 *
 * <p>Ids only: no employee, project or task name is copied into a response (W-41 decision 6). The picker reads names
 * from the project and task endpoints. Within a level the order is fixed so that a response is repeatable: projects
 * and tasks by id, days by date.
 */
public record TimesheetResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("week_start_date") LocalDate weekStartDate,
        @JsonProperty("week_end_date") LocalDate weekEndDate,
        @JsonProperty("status") TimesheetStatus status,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("projects") List<ProjectEntryResponse> projects,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    /** One project's lines, with its own status and, if it was rejected, the reason (W-42.3). */
    public record ProjectEntryResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("project_id") UUID projectId,
            @JsonProperty("status") TimesheetStatus status,
            @JsonProperty("rejection_reason") String rejectionReason,
            @JsonProperty("tasks") List<TaskEntryResponse> tasks) {}

    public record TaskEntryResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("task_id") UUID taskId,
            @JsonProperty("days") List<DayEntryResponse> days) {}

    public record DayEntryResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("date") LocalDate date,
            @JsonProperty("hours") BigDecimal hours,
            @JsonProperty("description") String description) {}

    /** Reads the whole nested timesheet; call inside the transaction that loaded it. */
    public static TimesheetResponse from(Timesheet timesheet) {
        List<ProjectEntryResponse> projects = timesheet.getProjects().stream()
                .sorted(Comparator.comparing(TimesheetProjectEntry::getProjectId))
                .map(TimesheetResponse::project)
                .toList();
        return new TimesheetResponse(
                timesheet.getId(),
                timesheet.getEmployeeId(),
                timesheet.getWeekStartDate(),
                timesheet.getWeekEndDate(),
                timesheet.getStatus(),
                timesheet.getSubmittedAt(),
                projects,
                timesheet.getCreatedAt(),
                timesheet.getUpdatedAt());
    }

    /**
     * The same week trimmed to the lines on the given projects, and never a draft line: what a project manager sees
     * (W-42.4). The week's own status and dates are shown as they are.
     */
    public static TimesheetResponse from(Timesheet timesheet, Set<UUID> onlyProjects) {
        TimesheetResponse whole = from(timesheet);
        List<ProjectEntryResponse> kept = whole.projects().stream()
                .filter(p -> onlyProjects.contains(p.projectId()) && p.status() != TimesheetStatus.DRAFT)
                .toList();
        return new TimesheetResponse(
                whole.id(),
                whole.employeeId(),
                whole.weekStartDate(),
                whole.weekEndDate(),
                whole.status(),
                whole.submittedAt(),
                kept,
                whole.createdAt(),
                whole.updatedAt());
    }

    private static ProjectEntryResponse project(TimesheetProjectEntry entry) {
        return new ProjectEntryResponse(
                entry.getId(), entry.getProjectId(), entry.getStatus(), entry.getRejectionReason(), tasksOf(entry));
    }

    /** The tasks of a project line, each with its days, in a fixed order. */
    static List<TaskEntryResponse> tasksOf(TimesheetProjectEntry entry) {
        return entry.getTasks().stream()
                .sorted(Comparator.comparing(TimesheetTaskEntry::getTaskId))
                .map(TimesheetResponse::task)
                .toList();
    }

    private static TaskEntryResponse task(TimesheetTaskEntry entry) {
        return new TaskEntryResponse(
                entry.getId(),
                entry.getTaskId(),
                entry.getDays().stream()
                        .sorted(Comparator.comparing(TimesheetDayEntry::getWorkDate))
                        .map(day -> new DayEntryResponse(
                                day.getId(), day.getWorkDate(), day.getHours(), day.getDescription()))
                        .toList());
    }
}
