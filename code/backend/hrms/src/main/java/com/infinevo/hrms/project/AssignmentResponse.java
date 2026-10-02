package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload for a project team assignment (W-41).
 */
public record AssignmentResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("assigned_on") LocalDate assignedOn,
        @JsonProperty("created_at") Instant createdAt) {

    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getProjectId(),
                assignment.getEmployeeId(),
                assignment.getAssignedOn(),
                assignment.getCreatedAt());
    }
}
