package com.infinevo.core.lop;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of resolving the working-day basis for an employee and period (W-18.1, 12-core-contracts.md §3).
 *
 * <p>Answers payable days, the divisor for that day, the {@code policyId} stamped onto pay figures
 * (W-18.2), and the policy's {@code lopRounding} — the rule W-18.2 applies once, at the boundary,
 * when it turns a loss-of-pay day into money.
 */
public record WorkingDayBasisResponse(
        BigDecimal payableDays, BigDecimal divisor, UUID policyId, LopRounding lopRounding) {}
