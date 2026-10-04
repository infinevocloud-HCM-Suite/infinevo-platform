package com.infinevo.hrms.timesheet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.hrms.timesheet.TimesheetResponse.TaskEntryResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one project line an approver is asked to decide (W-42.3 §4): which week and whose, the project, the tasks and days,
 * and the line's own status and rejection reason. Names are read at reply time (W-48.3 §4); a missing id leaves null.
 */
public record TimesheetProjectEntryResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("timesheet_id") UUID timesheetId,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("week_start_date") LocalDate weekStartDate,
        @JsonProperty("week_end_date") LocalDate weekEndDate,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("project_name") String projectName,
        @JsonProperty("status") TimesheetStatus status,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("tasks") List<TaskEntryResponse> tasks) {

    /** Reads the line, with its week, without names; call inside the transaction that loaded it. */
    static TimesheetProjectEntryResponse from(TimesheetProjectEntry entry) {
        return from(entry, Map.of(), Map.of(), Map.of());
    }

    /** Reads the line with names from the given maps; a missing id leaves its name null. */
    static TimesheetProjectEntryResponse from(
            TimesheetProjectEntry entry,
            Map<UUID, String> employeeNames,
            Map<UUID, String> projectNames,
            Map<UUID, String> taskTitles) {
        Timesheet sheet = entry.getTimesheet();
        return new TimesheetProjectEntryResponse(
                entry.getId(),
                sheet.getId(),
                sheet.getEmployeeId(),
                employeeNames.get(sheet.getEmployeeId()),
                sheet.getWeekStartDate(),
                sheet.getWeekEndDate(),
                entry.getProjectId(),
                projectNames.get(entry.getProjectId()),
                entry.getStatus(),
                entry.getRejectionReason(),
                TimesheetResponse.tasksOf(entry, taskTitles));
    }
}
