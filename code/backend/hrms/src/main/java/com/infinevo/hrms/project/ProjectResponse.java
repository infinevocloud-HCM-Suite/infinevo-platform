package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Response payload for a project (W-41).
 */
public record ProjectResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("category") String category,
        @JsonProperty("description") String description,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("priority") Priority priority,
        @JsonProperty("status") ProjectStatus status,
        @JsonProperty("progress") int progress,
        @JsonProperty("budget") BigDecimal budget,
        @JsonProperty("manager_employee_id") UUID managerEmployeeId,
        @JsonProperty("team_member_ids") List<UUID> teamMemberIds,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static ProjectResponse from(Project project, List<UUID> teamMemberIds) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getCategory(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.getPriority(),
                project.getStatus(),
                project.getProgress(),
                project.getBudget(),
                project.getManagerEmployeeId(),
                teamMemberIds != null ? teamMemberIds : Collections.emptyList(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    public static ProjectResponse from(Project project) {
        return from(project, Collections.emptyList());
    }
}
