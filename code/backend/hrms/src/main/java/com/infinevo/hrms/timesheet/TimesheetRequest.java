package com.infinevo.hrms.timesheet;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body to create or replace a draft timesheet (W-42.1 §4).
 *
 * <p>There is no employee in it: the timesheet is the caller's, from the login (the legacy DTO took the employee
 * from the body, so anyone could write a timesheet for anyone). Names are not in it either; ids only. Field names
 * are snake_case like the rest of the HRMS API, and the camelCase names in the spec are accepted as aliases.
 */
public record TimesheetRequest(
        @JsonProperty("week_start_date") @JsonAlias("weekStartDate") LocalDate weekStartDate,
        @JsonProperty("projects") List<ProjectLine> projects) {

    /** The hours worked on one project: its tasks, and under each the days. */
    public record ProjectLine(
            @JsonProperty("project_id") @JsonAlias("projectId") UUID projectId,
            @JsonProperty("tasks") List<TaskLine> tasks) {}

    /** The hours worked on one task of that project. */
    public record TaskLine(
            @JsonProperty("task_id") @JsonAlias("taskId") UUID taskId, @JsonProperty("days") List<DayLine> days) {}

    /** The hours worked on one day, with an optional note. */
    public record DayLine(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("hours") BigDecimal hours,
            @JsonProperty("description") String description) {}
}
