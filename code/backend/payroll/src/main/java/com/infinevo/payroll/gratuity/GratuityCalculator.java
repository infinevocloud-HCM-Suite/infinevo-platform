package com.infinevo.payroll.gratuity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Pure calculation engine for Payment of Gratuity Act, 1972 and Income Tax Section 10(10) (W-91).
 */
public final class GratuityCalculator {

    public static final BigDecimal STATUTORY_CEILING = new BigDecimal("2000000.00");
    private static final BigDecimal MULTIPLIER = new BigDecimal("15");
    private static final BigDecimal WORKING_DAYS_DIVISOR = new BigDecimal("26");
    private static final int MIN_SERVICE_YEARS = 5;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public record Result(
            boolean eligible,
            boolean exemptFrom5YrRule,
            int completedYears,
            BigDecimal lastDrawnBasic,
            BigDecimal lastDrawnDa,
            BigDecimal calculatedAmount,
            BigDecimal statutoryCeiling,
            BigDecimal grossGratuityPayable,
            BigDecimal taxExemptAmount,
            BigDecimal taxableAmount) {}

    private GratuityCalculator() {}

    /**
     * Computes gratuity eligibility, formula payout, statutory ceiling, and Section 10(10) split.
     */
    public static Result calculate(
            LocalDate joiningDate,
            LocalDate exitDate,
            GratuityReason reason,
            BigDecimal lastDrawnBasic,
            BigDecimal lastDrawnDa) {
        return calculate(joiningDate, exitDate, reason, lastDrawnBasic, lastDrawnDa, null);
    }

    /**
     * Overload allowing an explicit actual payout amount to test Section 10(10) taxable excess above
     * the statutory ₹20,00,000 ceiling.
     */
    public static Result calculate(
            LocalDate joiningDate,
            LocalDate exitDate,
            GratuityReason reason,
            BigDecimal lastDrawnBasic,
            BigDecimal lastDrawnDa,
            BigDecimal actualGratuityPaidOverride) {
        Objects.requireNonNull(joiningDate, "joiningDate must not be null");
        Objects.requireNonNull(exitDate, "exitDate must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (exitDate.isBefore(joiningDate)) {
            throw new IllegalArgumentException("exitDate must not be before joiningDate");
        }

        BigDecimal basic = normalizeMoney(lastDrawnBasic);
        BigDecimal da = normalizeMoney(lastDrawnDa);
        boolean exemptFrom5YrRule = reason.isExemptFrom5YrRule();

        Period servicePeriod = Period.between(joiningDate, exitDate);
        int rawYears = servicePeriod.getYears();
        boolean eligible = exemptFrom5YrRule || rawYears >= MIN_SERVICE_YEARS;
        int completedYears = roundCompletedYears(servicePeriod);

        if (!eligible || completedYears <= 0) {
            return new Result(
                    eligible, exemptFrom5YrRule, completedYears, basic, da, ZERO, STATUTORY_CEILING, ZERO, ZERO, ZERO);
        }

        BigDecimal wages = basic.add(da);
        BigDecimal calculatedAmount = MULTIPLIER
                .multiply(wages)
                .multiply(BigDecimal.valueOf(completedYears))
                .divide(WORKING_DAYS_DIVISOR, 2, RoundingMode.HALF_UP);

        BigDecimal grossPayable = actualGratuityPaidOverride != null
                ? normalizeMoney(actualGratuityPaidOverride)
                : calculatedAmount.min(STATUTORY_CEILING).setScale(2, RoundingMode.HALF_UP);

        // Section 10(10): least of (1) actual received, (2) ₹20,00,000 ceiling, (3) statutory formula
        BigDecimal taxExemptAmount =
                grossPayable.min(STATUTORY_CEILING).min(calculatedAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount =
                grossPayable.subtract(taxExemptAmount).max(ZERO).setScale(2, RoundingMode.HALF_UP);

        return new Result(
                true,
                exemptFrom5YrRule,
                completedYears,
                basic,
                da,
                calculatedAmount,
                STATUTORY_CEILING,
                grossPayable,
                taxExemptAmount,
                taxableAmount);
    }

    /**
     * Service period > 6 months is rounded up to the next full year; <= 6 months is rounded down.
     */
    static int roundCompletedYears(Period servicePeriod) {
        int years = servicePeriod.getYears();
        int months = servicePeriod.getMonths();
        int days = servicePeriod.getDays();
        if (months > 6 || (months == 6 && days > 0)) {
            return years + 1;
        }
        return years;
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Monetary amount must not be negative");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
