package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Response payload for a project team assignment (W-41).
 */
public record AssignmentResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("assigned_on") LocalDate assignedOn,
        @JsonProperty("created_at") Instant createdAt) {

    public static AssignmentResponse from(Assignment assignment) {
        return from(assignment, Map.of());
    }

    /** Builds the response with the employee name from {@code names}; a missing id gives {@code null}. */
    public static AssignmentResponse from(Assignment assignment, Map<UUID, String> names) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getProjectId(),
                assignment.getEmployeeId(),
                names != null ? names.get(assignment.getEmployeeId()) : null,
                assignment.getAssignedOn(),
                assignment.getCreatedAt());
    }
}
