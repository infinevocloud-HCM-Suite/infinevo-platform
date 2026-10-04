package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        @JsonProperty("manager_name") String managerName,
        @JsonProperty("team_member_ids") List<UUID> teamMemberIds,
        @JsonProperty("team") List<TeamMember> team,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    /** One team member with a display name, {@code null} when the employee is not found (W-48.1). */
    public record TeamMember(@JsonProperty("employee_id") UUID employeeId, @JsonProperty("name") String name) {}

    public static ProjectResponse from(Project project, List<UUID> teamMemberIds) {
        return from(project, teamMemberIds, Map.of());
    }

    /** Builds the response with names looked up in {@code names}; a missing id gives a {@code null} name. */
    public static ProjectResponse from(Project project, List<UUID> teamMemberIds, Map<UUID, String> names) {
        List<UUID> ids = teamMemberIds != null ? teamMemberIds : Collections.emptyList();
        Map<UUID, String> safe = names != null ? names : Map.of();
        UUID managerId = project.getManagerEmployeeId();
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
                managerId,
                managerId != null ? safe.get(managerId) : null,
                ids,
                ids.stream().map(id -> new TeamMember(id, safe.get(id))).toList(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    public static ProjectResponse from(Project project) {
        return from(project, Collections.emptyList());
    }
}
