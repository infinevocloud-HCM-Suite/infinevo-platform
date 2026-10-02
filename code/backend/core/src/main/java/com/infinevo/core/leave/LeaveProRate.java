package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Utility for calculating pro-rate factors and pro-rated leave entitlements (W-16.2, spec section 2 &amp; 6).
 */
public final class LeaveProRate {

    private LeaveProRate() {}

    /**
     * Calculates the pro-rate factor (scale 4, numeric(5,4)) for an employee across a leave year.
     *
     * @param yearStart start of the leave year (inclusive)
     * @param yearEnd end of the leave year (inclusive)
     * @param joinDate employee joining date (inclusive)
     * @param leaveDate employee leaving/termination date (optional, inclusive)
     * @return pro-rate factor between 0.0000 and 1.0000
     */
    public static BigDecimal calculateFactor(
            LocalDate yearStart, LocalDate yearEnd, LocalDate joinDate, LocalDate leaveDate) {
        Objects.requireNonNull(yearStart, "yearStart must not be null");
        Objects.requireNonNull(yearEnd, "yearEnd must not be null");
        if (yearStart.isAfter(yearEnd)) {
            throw new IllegalArgumentException("yearStart must not be after yearEnd");
        }

        LocalDate effectiveStart = yearStart;
        if (joinDate != null && joinDate.isAfter(yearStart)) {
            effectiveStart = joinDate;
        }

        LocalDate effectiveEnd = yearEnd;
        if (leaveDate != null && leaveDate.isBefore(yearEnd)) {
            effectiveEnd = leaveDate;
        }

        if (effectiveStart.isAfter(effectiveEnd)
                || (joinDate != null && joinDate.isAfter(yearEnd))
                || (leaveDate != null && leaveDate.isBefore(yearStart))) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        long totalDaysInYear = ChronoUnit.DAYS.between(yearStart, yearEnd) + 1;
        long activeDays = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;

        if (activeDays >= totalDaysInYear) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(activeDays).divide(BigDecimal.valueOf(totalDaysInYear), 4, RoundingMode.HALF_UP);
    }

    /**
     * Calculates pro-rated annual entitlement days (scale 2, numeric(10,2)).
     */
    public static BigDecimal calculateEntitlement(BigDecimal annualDays, BigDecimal factor) {
        if (annualDays == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (factor == null) {
            factor = BigDecimal.ONE;
        }
        return annualDays.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
