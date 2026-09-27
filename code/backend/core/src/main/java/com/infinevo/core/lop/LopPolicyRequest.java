package com.infinevo.core.lop;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for creating or updating a loss-of-pay policy version (W-18.1).
 */
public record LopPolicyRequest(
        WorkingDayBasis workingDayBasis,
        BigDecimal configuredDaysPerMonth,
        Boolean weekendsPayable,
        Boolean holidaysPayable,
        LopRounding lopRounding,
        LocalDate effectiveFrom) {}
