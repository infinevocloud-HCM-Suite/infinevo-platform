package com.infinevo.payroll.scheduled;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the officer schedules (W-73.6 §4): the component (must carry the Scheduled flag), the total
 * amount, the first month as {@code yyyy-MM}, the number of monthly instalments (1–12) and why.
 */
public record ScheduledEarningRequest(
        UUID componentId, BigDecimal amount, String firstPeriod, Integer instalments, String reason) {}
