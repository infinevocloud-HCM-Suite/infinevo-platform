package com.infinevo.hrms.timesheet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.hrms.timesheet.TimesheetResponse.TaskEntryResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The one project line an approver is asked to decide (W-42.3 §4): which week and whose, the project, the tasks and days,
 * and the line's own status and rejection reason. Ids only, as {@link TimesheetResponse}: no names are copied.
 */
public record TimesheetProjectEntryResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("timesheet_id") UUID timesheetId,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("week_start_date") LocalDate weekStartDate,
        @JsonProperty("week_end_date") LocalDate weekEndDate,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("status") TimesheetStatus status,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("tasks") List<TaskEntryResponse> tasks) {

    /** Reads the line, with its week; call inside the transaction that loaded it. */
    static TimesheetProjectEntryResponse from(TimesheetProjectEntry entry) {
        Timesheet sheet = entry.getTimesheet();
        return new TimesheetProjectEntryResponse(
                entry.getId(),
                sheet.getId(),
                sheet.getEmployeeId(),
                sheet.getWeekStartDate(),
                sheet.getWeekEndDate(),
                entry.getProjectId(),
                entry.getStatus(),
                entry.getRejectionReason(),
                TimesheetResponse.tasksOf(entry));
    }
}
