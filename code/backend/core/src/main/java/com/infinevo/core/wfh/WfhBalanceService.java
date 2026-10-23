package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface WfhBalanceService {

    WfhBalanceSummaryResponse getBalanceSummary(UUID tenantId, UUID employeeId, LocalDate asOf);

    BigDecimal getAvailableDays(UUID tenantId, UUID employeeId, LocalDate asOf);

    boolean isWithinMonthlyLimit(UUID tenantId, UUID employeeId, LocalDate date, BigDecimal requestedDays);
}
