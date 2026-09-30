package com.infinevo.payroll.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response payload describing a tenant's pay schedule (W-28 §4). Keys match the request's
 * snake_case names, so what a client PUTs is what it GETs back.
 */
public record PayScheduleResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("frequency") String frequency,
        @JsonProperty("working_days") List<Integer> workingDays,
        @JsonProperty("pay_day_rule") PayDayRule payDayRule,
        @JsonProperty("pay_day_of_month") Integer payDayOfMonth,
        @JsonProperty("input_cutoff_day") int inputCutoffDay,
        @JsonProperty("first_period_start") LocalDate firstPeriodStart,
        @JsonProperty("exists") boolean exists) {

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
