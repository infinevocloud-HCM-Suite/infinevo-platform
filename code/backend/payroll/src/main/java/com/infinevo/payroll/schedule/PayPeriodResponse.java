package com.infinevo.payroll.schedule;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Derived dates for a single monthly pay period (W-28 §4).
 */
public record PayPeriodResponse(
        @JsonProperty("start") LocalDate start,
        @JsonProperty("end") LocalDate end,
        @JsonProperty("cutoffDate") @JsonAlias("cutoff_date") LocalDate cutoffDate,
        @JsonProperty("payDate") @JsonAlias("pay_date") LocalDate payDate) {

    @JsonProperty("cutoff_date")
    public LocalDate getCutoff_date() {
        return cutoffDate;
    }

    @JsonProperty("pay_date")
    public LocalDate getPay_date() {
        return payDate;
    }
}
