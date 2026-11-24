package com.infinevo.payroll.taxcalc;

import com.infinevo.shared.money.Money;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Rounds taxable income to the rupee without letting it exceed gross total income (D-147).
 *
 * <p>Half-up rounding alone lifts a taxable income of {@code x.50} or more above a gross total income that
 * carries the same paise (no Chapter VI-A, or no standard deduction left), and the TDS record then refuses
 * it ("taxable must not exceed gross", {@code EmployeeTdsServiceImpl}). When half-up would cross gross, the
 * figure is rounded down instead, so it stays a whole rupee and never exceeds gross.
 */
final class TaxableIncomeRounding {

    private TaxableIncomeRounding() {}

    static Money toRupee(Money taxableRaw, Money grossTotalIncome) {
        Objects.requireNonNull(taxableRaw, "taxableRaw must not be null");
        Objects.requireNonNull(grossTotalIncome, "grossTotalIncome must not be null");
        Money rounded = Money.of(taxableRaw.raw().setScale(0, RoundingMode.HALF_UP));
        if (rounded.compareTo(grossTotalIncome) <= 0) {
            return rounded;
        }
        return Money.of(taxableRaw.raw().setScale(0, RoundingMode.FLOOR));
    }
}
