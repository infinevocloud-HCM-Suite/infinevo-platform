package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.util.UUID;

public record WfhBalanceSummaryResponse(
        UUID employeeId,
        String wfhYear,
        BigDecimal entitledDays,
        BigDecimal consumedDays,
        BigDecimal pendingDays,
        BigDecimal availableDays,
        BigDecimal monthlyLimitDays,
        BigDecimal monthConsumedDays) {}
