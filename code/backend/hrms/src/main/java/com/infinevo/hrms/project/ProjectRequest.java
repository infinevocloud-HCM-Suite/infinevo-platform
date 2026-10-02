package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for creating or updating a project (W-41).
 */
public record ProjectRequest(
        @NotBlank(message = "name is required") @JsonProperty("name") String name,
        @JsonProperty("category") String category,
        @JsonProperty("description") String description,
        @JsonProperty("start_date") @JsonAlias("startDate") LocalDate startDate,
        @JsonProperty("end_date") @JsonAlias("endDate") LocalDate endDate,
        @NotNull(message = "priority is required") @JsonProperty("priority") Priority priority,
        @JsonProperty("status") ProjectStatus status,
        @JsonProperty("budget") BigDecimal budget,
        @JsonProperty("manager_employee_id") @JsonAlias("managerEmployeeId") UUID managerEmployeeId) {}
