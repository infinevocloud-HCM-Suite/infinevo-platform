package com.infinevo.payroll.payrun;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * The periodic rule for a variable earning (W-29.2 §3), from the legacy {@code getPeriodicBonusAmount}
 * ({@code EmployeePayRunServiceImpl.java:449-520}), plus {@code MONTHLY}, for which legacy paid zero.
 *
 * <table>
 *   <tr><th>Frequency</th><th>Paid in</th><th>Amount</th><th>Unless months completed</th></tr>
 *   <tr><td>MONTHLY</td><td>every period</td><td>monthly amount</td><td>—</td></tr>
 *   <tr><td>QUARTERLY</td><td>Jan, Apr, Jul, Oct</td><td>annual / 4</td><td>&lt; 3</td></tr>
 *   <tr><td>HALF_YEARLY</td><td>Jan, Jul</td><td>annual / 2</td><td>&lt; 6</td></tr>
 *   <tr><td>YEARLY</td><td>Jan</td><td>annual</td><td>&lt; 12</td></tr>
 * </table>
 *
 * <p>Months completed are whole months from the joining month to the period. Division is
 * {@link Money#divide} at scale 4 — nothing here rounds to 2.
 */
public final class PeriodicEarningRule {

    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private PeriodicEarningRule() {}

    /** The amount paid in {@code period}, or empty when this period pays nothing for the earning. */
    public static Optional<Money> amountFor(
            EarningFrequency frequency,
            Money monthlyAmount,
            Money annualAmount,
            YearMonth period,
            LocalDate dateOfJoining) {
        Objects.requireNonNull(frequency, "frequency must not be null");
        Objects.requireNonNull(period, "period must not be null");
        long monthsCompleted = dateOfJoining == null
                ? Long.MAX_VALUE
                : ChronoUnit.MONTHS.between(YearMonth.from(dateOfJoining), period);
        int month = period.getMonthValue();
        return switch (frequency) {
            case MONTHLY -> Optional.of(Objects.requireNonNull(monthlyAmount, "monthlyAmount must not be null"));
            case QUARTERLY ->
                (month == 1 || month == 4 || month == 7 || month == 10) && monthsCompleted >= 3
                        ? Optional.of(annual(annualAmount).divide(FOUR))
                        : Optional.empty();
            case HALF_YEARLY ->
                (month == 1 || month == 7) && monthsCompleted >= 6
                        ? Optional.of(annual(annualAmount).divide(TWO))
                        : Optional.empty();
            case YEARLY -> month == 1 && monthsCompleted >= 12 ? Optional.of(annual(annualAmount)) : Optional.empty();
        };
    }

    private static Money annual(Money annualAmount) {
        return Objects.requireNonNull(annualAmount, "annualAmount must not be null");
    }
}
