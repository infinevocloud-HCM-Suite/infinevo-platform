package com.infinevo.core.overtime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** {@code POST /api/v1/overtime} request body (W-39.2 §4). */
public record OvertimeEntry(
        @JsonProperty("employee_id") @JsonAlias("employeeId") UUID employeeId,
        @JsonProperty("overtime_date") @JsonAlias("overtimeDate") LocalDate overtimeDate,
        @JsonProperty("hours") BigDecimal hours,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("remarks") String remarks) {}
