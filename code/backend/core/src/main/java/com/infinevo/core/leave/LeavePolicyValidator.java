package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * Validates leave policy requests according to W-16.1 specifications.
 */
public final class LeavePolicyValidator {

    private static final Set<String> ALLOWED_DIMENSIONS = Set.of("department", "designation", "work_location");

    private LeavePolicyValidator() {}

    public static void validate(LeavePolicyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Leave policy request must not be null");
        }
        if (request.annualDays() == null || request.annualDays().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("annualDays must not be null and must be non-negative");
        }
        if (request.effectiveFrom() == null) {
            throw new IllegalArgumentException("effectiveFrom must not be null");
        }
        if (request.exceedBalanceMode() == null) {
            throw new IllegalArgumentException("exceedBalanceMode must not be null");
        }

        // Exceed balance mode limit enforcement (W-16.1 spec section 4 & 6)
        if (request.exceedBalanceMode() == ExceedBalanceMode.YEAR_END_LIMIT) {
            if (request.exceedBalanceLimitDays() == null) {
                throw new IllegalArgumentException(
                        "exceedBalanceLimitDays is required when exceedBalanceMode is yearEndLimit");
            }
            if (request.exceedBalanceLimitDays().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("exceedBalanceLimitDays must be non-negative");
            }
        } else {
            if (request.exceedBalanceLimitDays() != null) {
                throw new IllegalArgumentException(
                        "exceedBalanceLimitDays must be null when exceedBalanceMode is not yearEndLimit");
            }
        }

        // Accrual validation
        if (Boolean.TRUE.equals(request.accrualEnabled())) {
            if (request.accrualFrequency() == null) {
                throw new IllegalArgumentException("accrualFrequency is required when accrual is enabled");
            }
            if (request.accrualUnits() == null || request.accrualUnits().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("accrualUnits must be positive when accrual is enabled");
            }
        }

        // Reset validation
        if (Boolean.TRUE.equals(request.resetEnabled())) {
            if (request.resetFrequency() == null) {
                throw new IllegalArgumentException("resetFrequency is required when reset is enabled");
            }
        }

        // Carry forward validation
        if (Boolean.TRUE.equals(request.carryForwardEnabled())) {
            if (request.carryForwardCap() == null || request.carryForwardCap().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "carryForwardCap must be non-negative when carry forward is enabled");
            }
        }

        // Eligibility dimensions validation
        if (request.eligibility() != null) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (LeavePolicyEligibilityRequest elig : request.eligibility()) {
                if (elig.dimension() == null || elig.dimension().isBlank()) {
                    throw new IllegalArgumentException("Eligibility dimension must not be blank");
                }
                String dim = elig.dimension().trim().toLowerCase(Locale.ROOT);
                if ("employment_type".equals(dim)) {
                    throw new IllegalArgumentException(
                            "employment_type dimension is not supported until employment type master exists");
                }
                if (!ALLOWED_DIMENSIONS.contains(dim)) {
                    throw new IllegalArgumentException("Unsupported eligibility dimension: '" + elig.dimension()
                            + "'. Allowed: department, designation, work_location");
                }
                if (elig.valueId() == null) {
                    throw new IllegalArgumentException("Eligibility valueId must not be null");
                }
                if (!seen.add(dim + ":" + elig.valueId())) {
                    throw new IllegalArgumentException("Eligibility row is repeated: " + dim + " " + elig.valueId());
                }
            }
        }
    }
}
