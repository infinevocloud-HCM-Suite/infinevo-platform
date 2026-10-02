package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /api/v1/payroll/payruns/off-cycle} (W-30.2 §4): the pay date, the employees to pay and
 * an optional note. The period is the pay date's month; the other dates come from the schedule.
 */
public record CreateOffCyclePayRunRequest(
        @JsonProperty("pay_date") @JsonAlias("payDate") LocalDate payDate,
        @JsonProperty("employee_ids") @JsonAlias("employeeIds") List<UUID> employeeIds,
        @JsonProperty("notes") String notes) {}
