package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for configuring a leave policy matrix (W-16.1, spec section 4 &amp; 6).
 */
public record LeavePolicyRequest(
        BigDecimal annualDays,
        Boolean accrualEnabled,
        AccrualFrequency accrualFrequency,
        BigDecimal accrualUnits,
        Boolean resetEnabled,
        ResetFrequency resetFrequency,
        Boolean carryForwardEnabled,
        BigDecimal carryForwardCap,
        Integer carryForwardExpiresAfterMonths,
        Boolean requiresDocument,
        Integer pastBookingLimitDays,
        Integer futureBookingLimitDays,
        Boolean includeWeekend,
        Boolean includeHoliday,
        ExceedBalanceMode exceedBalanceMode,
        BigDecimal exceedBalanceLimitDays,
        Boolean proRateEnabled,
        BigDecimal maxDaysPerApplication,
        String gender,
        LocalDate effectiveFrom,
        List<LeavePolicyEligibilityRequest> eligibility) {}
