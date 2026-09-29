package com.infinevo.payroll.schedule;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for upserting a pay schedule (W-28 §4).
 */
public record PayScheduleRequest(
        @JsonProperty("workingDays") @JsonAlias("working_days") List<Integer> workingDays,
        @JsonProperty("payDayRule") @JsonAlias("pay_day_rule") PayDayRule payDayRule,
        @JsonProperty("payDayOfMonth") @JsonAlias("pay_day_of_month") Integer payDayOfMonth,
        @JsonProperty("inputCutoffDay") @JsonAlias("input_cutoff_day") Integer inputCutoffDay,
        @JsonProperty("firstPeriodStart") @JsonAlias("first_period_start") LocalDate firstPeriodStart) {}
