package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Response payload for a task (W-41).
 */
public record TaskResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("project_name") String projectName,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("assignee_employee_id") UUID assigneeEmployeeId,
        @JsonProperty("assignee_name") String assigneeName,
        @JsonProperty("due_date") LocalDate dueDate,
        @JsonProperty("priority") Priority priority,
        @JsonProperty("status") TaskStatus status,
        @JsonProperty("estimated_hours") Integer estimatedHours,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static TaskResponse from(Task task) {
        return from(task, Map.of(), Map.of());
    }

    /** Builds the response with names from the two maps; a missing id gives a {@code null} name. */
    public static TaskResponse from(Task task, Map<UUID, String> employeeNames, Map<UUID, String> projectNames) {
        UUID assignee = task.getAssigneeEmployeeId();
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                projectNames != null ? projectNames.get(task.getProjectId()) : null,
                task.getTitle(),
                task.getDescription(),
                assignee,
                assignee != null && employeeNames != null ? employeeNames.get(assignee) : null,
                task.getDueDate(),
                task.getPriority(),
                task.getStatus(),
                task.getEstimatedHours(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
