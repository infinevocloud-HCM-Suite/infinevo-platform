package com.infinevo.payroll.payrun;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The day figures behind one employee's loss of pay (W-29.3 §3), all at scale 2 but {@code pricedDays}:
 *
 * <pre>
 * lop_days    = Σ LOP_DAYS inputs, capped at payable days, never below zero
 * credit_days = the days by which that sum is below zero; not capped, two months can come back at once
 * outside     = the period's days before date_of_joining or after termination_date, in the policy's days
 * unpaid_days = lop_days + outside
 * paid_days   = payable days − unpaid_days, floor 0
 * </pre>
 *
 * <p>Days outside the employment window come from W-18.1's calculator
 * ({@code WorkingDayBasisCalculator.daysOutsideEmployment}), counted the way the policy counts its
 * divisor — working days under a working-day basis, the gap's share of the month under a fixed one —
 * so {@link LopLineContributor} divides like by like (W-18.2). Calendar days divided by a working-day
 * divisor underpaid every joiner and leaver under such a policy. Nothing here counts calendar days.
 *
 * <p>{@code pricedDays} is {@code unpaid_days} before it is rounded to scale 2, and is what the money is
 * computed from: a fixed basis's share of the month is rarely a whole number of days, and pricing the
 * rounded figure would round the amount twice. The scale-2 figures are what the row records.
 *
 * <p>The sum goes below zero when a {@code LOP_DAYS} reversal lands in a later period than the row it
 * reverses — W-19 moves a reversal of a locked period to the next open one. Those days were deducted
 * in a month already paid, so they are owed back: {@code credit_days} carries them to
 * {@link LopLineContributor}, which writes the refund. They are not {@code paid_days} of this period.
 */
public record PayRunDays(
        BigDecimal lopDays,
        BigDecimal outsideDays,
        BigDecimal unpaidDays,
        BigDecimal paidDays,
        BigDecimal creditDays,
        BigDecimal pricedDays) {

    private static final int SCALE = 2;

    public PayRunDays {
        Objects.requireNonNull(lopDays, "lopDays must not be null");
        Objects.requireNonNull(outsideDays, "outsideDays must not be null");
        Objects.requireNonNull(unpaidDays, "unpaidDays must not be null");
        Objects.requireNonNull(paidDays, "paidDays must not be null");
        Objects.requireNonNull(creditDays, "creditDays must not be null");
        Objects.requireNonNull(pricedDays, "pricedDays must not be null");
    }

    /**
     * @param outsideDays the period's days outside the employment window, in the policy's days and
     *     unrounded — zero for someone employed all period
     */
    public static PayRunDays of(BigDecimal payableDays, BigDecimal requestedLopDays, BigDecimal outsideDays) {
        Objects.requireNonNull(payableDays, "payableDays must not be null");
        Objects.requireNonNull(outsideDays, "outsideDays must not be null");

        BigDecimal requested = requestedLopDays == null ? BigDecimal.ZERO : requestedLopDays;
        BigDecimal lop = requested.max(BigDecimal.ZERO).min(payableDays);
        BigDecimal credit = requested.negate().max(BigDecimal.ZERO);
        BigDecimal outside = outsideDays.max(BigDecimal.ZERO).min(payableDays);

        BigDecimal unpaid = lop.add(outside);
        BigDecimal paid = payableDays.subtract(unpaid).max(BigDecimal.ZERO);
        return new PayRunDays(scaled(lop), scaled(outside), scaled(unpaid), scaled(paid), scaled(credit), unpaid);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
