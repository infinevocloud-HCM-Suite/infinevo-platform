package com.infinevo.payroll.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Derived dates for a single monthly pay period (W-28 §4). Serialised with the spec's keys:
 * {@code start}, {@code end}, {@code cutoff_date}, {@code pay_date} — one spelling each.
 */
public record PayPeriodResponse(
        @JsonProperty("start") LocalDate start,
        @JsonProperty("end") LocalDate end,
        @JsonProperty("cutoff_date") LocalDate cutoffDate,
        @JsonProperty("pay_date") LocalDate payDate) {}
