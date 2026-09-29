package com.infinevo.core.lop;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of resolving the working-day basis for an employee and period (W-18.1, 12-core-contracts.md §3).
 *
 * <p>Answers payable days, the divisor for that day, and the {@code policyId} stamped onto pay figures (W-18.2).
 */
public record WorkingDayBasisResponse(BigDecimal payableDays, BigDecimal divisor, UUID policyId) {}
