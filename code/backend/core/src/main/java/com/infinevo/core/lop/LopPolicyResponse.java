package com.infinevo.core.lop;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload describing a loss-of-pay policy version (W-18.1).
 */
public record LopPolicyResponse(
        UUID id,
        UUID tenantId,
        WorkingDayBasis workingDayBasis,
        BigDecimal configuredDaysPerMonth,
        boolean weekendsPayable,
        boolean holidaysPayable,
        LopRounding lopRounding,
        LocalDate effectiveFrom) {}
