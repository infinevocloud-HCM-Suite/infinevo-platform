package com.infinevo.payroll.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for upserting a pay schedule (W-28 §4). Keys are the spec's snake_case names.
 */
public record PayScheduleRequest(
        @JsonProperty("working_days") List<Integer> workingDays,
        @JsonProperty("pay_day_rule") PayDayRule payDayRule,
        @JsonProperty("pay_day_of_month") Integer payDayOfMonth,
        @JsonProperty("input_cutoff_day") Integer inputCutoffDay,
        @JsonProperty("first_period_start") LocalDate firstPeriodStart) {}
