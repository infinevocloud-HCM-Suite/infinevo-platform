package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for assigning an employee to a project (W-41).
 */
public record AssignmentRequest(
        @NotNull(message = "employee_id is required") @JsonProperty("employee_id") @JsonAlias("employeeId")
                UUID employeeId,
        @JsonProperty("assigned_on") @JsonAlias("assignedOn") LocalDate assignedOn) {}
