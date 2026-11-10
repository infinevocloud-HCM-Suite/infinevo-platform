package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What "Refresh scheduled earnings" did: how many ledger rows this call wrote for the run's month. */
public record ScheduledEarningsRefreshResponse(@JsonProperty("written") int written) {}
