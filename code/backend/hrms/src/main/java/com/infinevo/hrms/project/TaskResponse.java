package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload for a task (W-41).
 */
public record TaskResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("assignee_employee_id") UUID assigneeEmployeeId,
        @JsonProperty("due_date") LocalDate dueDate,
        @JsonProperty("priority") Priority priority,
        @JsonProperty("status") TaskStatus status,
        @JsonProperty("estimated_hours") Integer estimatedHours,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getDescription(),
                task.getAssigneeEmployeeId(),
                task.getDueDate(),
                task.getPriority(),
                task.getStatus(),
                task.getEstimatedHours(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
