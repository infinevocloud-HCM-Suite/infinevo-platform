package com.infinevo.core.lop;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for creating or updating a loss-of-pay policy version (W-18.1).
 */
public record LopPolicyRequest(
        @NotNull WorkingDayBasis workingDayBasis,
        BigDecimal configuredDaysPerMonth,
        Boolean weekendsPayable,
        Boolean holidaysPayable,
        LopRounding lopRounding,
        @NotNull LocalDate effectiveFrom) {}
