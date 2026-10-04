package com.infinevo.hrms.timesheet;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A timesheet as the API returns it, nested project, task and day (W-42.1 §4).
 *
 * <p>Names are read for display at reply time (W-48.3 §4), never stored: {@code employee_name}, {@code project_name}
 * and {@code task_title} are null when the {@code from} overload without names is used or the id has no live row. Within a level the order is fixed so that a response is repeatable: projects
 * and tasks by id, days by date.
 */
public record TimesheetResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("employee_name") String employeeName,
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
            @JsonProperty("project_name") String projectName,
            @JsonProperty("status") TimesheetStatus status,
            @JsonProperty("rejection_reason") String rejectionReason,
            @JsonProperty("tasks") List<TaskEntryResponse> tasks) {}

    public record TaskEntryResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("task_id") UUID taskId,
            @JsonProperty("task_title") String taskTitle,
            @JsonProperty("days") List<DayEntryResponse> days) {}

    public record DayEntryResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("date") LocalDate date,
            @JsonProperty("hours") BigDecimal hours,
            @JsonProperty("description") String description) {}

    /** Reads the whole nested timesheet, without names; call inside the transaction that loaded it. */
    public static TimesheetResponse from(Timesheet timesheet) {
        return from(timesheet, Map.of(), Map.of(), Map.of());
    }

    /** Reads the whole nested timesheet with names from the given maps; a missing id leaves its name null. */
    public static TimesheetResponse from(
            Timesheet timesheet,
            Map<UUID, String> employeeNames,
            Map<UUID, String> projectNames,
            Map<UUID, String> taskTitles) {
        List<ProjectEntryResponse> projects = timesheet.getProjects().stream()
                .sorted(Comparator.comparing(TimesheetProjectEntry::getProjectId))
                .map(entry -> project(entry, projectNames, taskTitles))
                .toList();
        return new TimesheetResponse(
                timesheet.getId(),
                timesheet.getEmployeeId(),
                employeeNames.get(timesheet.getEmployeeId()),
                timesheet.getWeekStartDate(),
                timesheet.getWeekEndDate(),
                timesheet.getStatus(),
                timesheet.getSubmittedAt(),
                projects,
                timesheet.getCreatedAt(),
                timesheet.getUpdatedAt());
    }

    static TimesheetResponse from(Timesheet timesheet, TimesheetNames.Names names) {
        return from(timesheet, names.employees(), names.projects(), names.tasks());
    }

    /**
     * The same week trimmed to the lines on the given projects, and never a draft line: what a project manager sees
     * (W-42.4). The week's own status and dates are shown as they are.
     */
    public static TimesheetResponse from(Timesheet timesheet, Set<UUID> onlyProjects) {
        return from(timesheet, onlyProjects, TimesheetNames.Names.NONE);
    }

    static TimesheetResponse from(Timesheet timesheet, Set<UUID> onlyProjects, TimesheetNames.Names names) {
        TimesheetResponse whole = from(timesheet, names);
        List<ProjectEntryResponse> kept = whole.projects().stream()
                .filter(p -> onlyProjects.contains(p.projectId()) && p.status() != TimesheetStatus.DRAFT)
                .toList();
        return new TimesheetResponse(
                whole.id(),
                whole.employeeId(),
                whole.employeeName(),
                whole.weekStartDate(),
                whole.weekEndDate(),
                whole.status(),
                whole.submittedAt(),
                kept,
                whole.createdAt(),
                whole.updatedAt());
    }

    private static ProjectEntryResponse project(
            TimesheetProjectEntry entry, Map<UUID, String> projectNames, Map<UUID, String> taskTitles) {
        return new ProjectEntryResponse(
                entry.getId(),
                entry.getProjectId(),
                projectNames.get(entry.getProjectId()),
                entry.getStatus(),
                entry.getRejectionReason(),
                tasksOf(entry, taskTitles));
    }

    /** The tasks of a project line, each with its days, in a fixed order. */
    static List<TaskEntryResponse> tasksOf(TimesheetProjectEntry entry, Map<UUID, String> taskTitles) {
        return entry.getTasks().stream()
                .sorted(Comparator.comparing(TimesheetTaskEntry::getTaskId))
                .map(task -> task(task, taskTitles))
                .toList();
    }

    private static TaskEntryResponse task(TimesheetTaskEntry entry, Map<UUID, String> taskTitles) {
        return new TaskEntryResponse(
                entry.getId(),
                entry.getTaskId(),
                taskTitles.get(entry.getTaskId()),
                entry.getDays().stream()
                        .sorted(Comparator.comparing(TimesheetDayEntry::getWorkDate))
                        .map(day -> new DayEntryResponse(
                                day.getId(), day.getWorkDate(), day.getHours(), day.getDescription()))
                        .toList());
    }
}
