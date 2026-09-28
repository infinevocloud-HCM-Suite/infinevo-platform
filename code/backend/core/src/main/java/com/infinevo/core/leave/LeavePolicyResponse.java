package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Response representation of a leave policy matrix (W-16.1).
 */
public record LeavePolicyResponse(
        UUID id,
        UUID leaveTypeId,
        BigDecimal annualDays,
        Boolean accrualEnabled,
        AccrualFrequency accrualFrequency,
        BigDecimal accrualUnits,
        Boolean resetEnabled,
        ResetFrequency resetFrequency,
        Boolean carryForwardEnabled,
        BigDecimal carryForwardCap,
        Integer carryForwardExpiresAfterMonths,
        boolean requiresDocument,
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
        List<LeavePolicyEligibilityResponse> eligibility,
        Instant createdAt,
        Instant updatedAt) {

    public static LeavePolicyResponse from(LeavePolicy policy, List<LeavePolicyEligibility> eligibilities) {
        if (policy == null) {
            return null;
        }
        List<LeavePolicyEligibilityResponse> eligibilityResponses = eligibilities != null
                ? eligibilities.stream()
                        .map(LeavePolicyEligibilityResponse::from)
                        .toList()
                : Collections.emptyList();

        return new LeavePolicyResponse(
                policy.getId(),
                policy.getLeaveTypeId(),
                policy.getAnnualDays(),
                policy.getAccrualEnabled(),
                policy.getAccrualFrequency(),
                policy.getAccrualUnits(),
                policy.getResetEnabled(),
                policy.getResetFrequency(),
                policy.getCarryForwardEnabled(),
                policy.getCarryForwardCap(),
                policy.getCarryForwardExpiresAfterMonths(),
                policy.isRequiresDocument(),
                policy.getPastBookingLimitDays(),
                policy.getFutureBookingLimitDays(),
                policy.getIncludeWeekend(),
                policy.getIncludeHoliday(),
                policy.getExceedBalanceMode(),
                policy.getExceedBalanceLimitDays(),
                policy.getProRateEnabled(),
                policy.getMaxDaysPerApplication(),
                policy.getGender(),
                policy.getEffectiveFrom(),
                eligibilityResponses,
                policy.getCreatedAt(),
                policy.getUpdatedAt());
    }
}
