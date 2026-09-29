package com.infinevo.payroll.schedule;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response payload describing a tenant's pay schedule (W-28 §4).
 */
public record PayScheduleResponse(
        UUID id,
        UUID tenantId,
        String frequency,
        @JsonProperty("workingDays") @JsonAlias("working_days") List<Integer> workingDays,
        @JsonProperty("payDayRule") @JsonAlias("pay_day_rule") PayDayRule payDayRule,
        @JsonProperty("payDayOfMonth") @JsonAlias("pay_day_of_month") Integer payDayOfMonth,
        @JsonProperty("inputCutoffDay") @JsonAlias("input_cutoff_day") int inputCutoffDay,
        @JsonProperty("firstPeriodStart") @JsonAlias("first_period_start") LocalDate firstPeriodStart,
        boolean exists) {

    public static PayScheduleResponse from(PaySchedule schedule, boolean exists) {
        return new PayScheduleResponse(
                schedule.getId(),
                schedule.getTenantId(),
                schedule.getFrequency(),
                schedule.getWorkingDaysAsList(),
                schedule.getPayDayRule(),
                schedule.getPayDayOfMonth() != null
                        ? schedule.getPayDayOfMonth().intValue()
                        : null,
                schedule.getInputCutoffDay(),
                schedule.getFirstPeriodStart(),
                exists);
    }

    public static PayScheduleResponse defaultMissing(UUID tenantId) {
        return new PayScheduleResponse(
                null,
                tenantId,
                PaySchedule.DEFAULT_FREQUENCY,
                List.of(1, 2, 3, 4, 5),
                PayDayRule.LAST_WORKING_DAY,
                null,
                PaySchedule.DEFAULT_INPUT_CUTOFF_DAY,
                null,
                false);
    }
}
