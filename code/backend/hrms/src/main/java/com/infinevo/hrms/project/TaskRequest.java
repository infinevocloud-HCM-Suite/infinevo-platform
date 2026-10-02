package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for creating or updating a task (W-41).
 */
public record TaskRequest(
        @NotBlank(message = "title is required") @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("assignee_employee_id") @JsonAlias("assigneeEmployeeId") UUID assigneeEmployeeId,
        @JsonProperty("due_date") @JsonAlias("dueDate") LocalDate dueDate,
        @NotNull(message = "priority is required") @JsonProperty("priority") Priority priority,
        @JsonProperty("status") TaskStatus status,
        @JsonProperty("estimated_hours") @JsonAlias("estimatedHours") Integer estimatedHours) {}
