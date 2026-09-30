package com.infinevo.payroll.payrun;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The day figures behind one employee's loss of pay (W-29.3 §3), all at scale 2:
 *
 * <pre>
 * lop_days    = Σ LOP_DAYS inputs, capped at payable days, never below zero
 * credit_days = the days by which that sum is below zero, capped at payable days
 * outside     = calendar days of the period before date_of_joining or after termination_date
 * unpaid_days = lop_days + outside
 * paid_days   = payable days − unpaid_days, floor 0
 * </pre>
 *
 * <p>Days outside the employment window are calendar days, converted by the policy divisor in
 * {@link LopLineContributor} — the legacy rule for joiners, now applied to leavers too (W-29.3 §13
 * decision 2).
 *
 * <p>The sum goes below zero when a {@code LOP_DAYS} reversal lands in a later period than the row it
 * reverses — W-19 moves a reversal of a locked period to the next open one. Those days were deducted
 * in a month already paid, so they are owed back: {@code credit_days} carries them to
 * {@link LopLineContributor}, which writes the refund. They are not {@code paid_days} of this period.
 */
public record PayRunDays(
        BigDecimal lopDays, BigDecimal outsideDays, BigDecimal unpaidDays, BigDecimal paidDays, BigDecimal creditDays) {

    private static final int SCALE = 2;

    public PayRunDays {
        Objects.requireNonNull(lopDays, "lopDays must not be null");
        Objects.requireNonNull(outsideDays, "outsideDays must not be null");
        Objects.requireNonNull(unpaidDays, "unpaidDays must not be null");
        Objects.requireNonNull(paidDays, "paidDays must not be null");
        Objects.requireNonNull(creditDays, "creditDays must not be null");
    }

    public static PayRunDays of(
            BigDecimal payableDays,
            BigDecimal requestedLopDays,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate dateOfJoining,
            LocalDate terminationDate) {
        Objects.requireNonNull(payableDays, "payableDays must not be null");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");

        BigDecimal requested = requestedLopDays == null ? BigDecimal.ZERO : requestedLopDays;
        BigDecimal lop = requested.max(BigDecimal.ZERO).min(payableDays);
        BigDecimal credit = requested.negate().max(BigDecimal.ZERO).min(payableDays);

        long calendarDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        long before = 0;
        if (dateOfJoining != null && dateOfJoining.isAfter(periodStart)) {
            before = Math.min(ChronoUnit.DAYS.between(periodStart, dateOfJoining), calendarDays);
        }
        long after = 0;
        if (terminationDate != null && terminationDate.isBefore(periodEnd)) {
            after = Math.min(ChronoUnit.DAYS.between(terminationDate, periodEnd), calendarDays);
        }
        BigDecimal outside = BigDecimal.valueOf(Math.min(before + after, calendarDays));

        BigDecimal unpaid = lop.add(outside);
        BigDecimal paid = payableDays.subtract(unpaid).max(BigDecimal.ZERO);
        return new PayRunDays(scaled(lop), scaled(outside), scaled(unpaid), scaled(paid), scaled(credit));
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
