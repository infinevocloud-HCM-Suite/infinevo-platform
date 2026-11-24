package com.infinevo.payroll.tds;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * How the annual tax of a TDS record spreads over the months left in the financial year (W-36.1 §4, D-145).
 *
 * <p>One calculation for the pay run line ({@link TaxLineContributor}) and for the officer's figures on
 * {@code GET .../tds/{fy}}. Remaining is the annual tax less the tax already deducted this year; months left
 * run from the later of the effective month and the pay month to March; the monthly share is remaining
 * divided by months left. The pay run passes the month it is paying, with the year to date excluding that
 * run. The officer's card passes the month the next regular run will cover - the first month of the year,
 * not before the effective month, with no computed, approved or paid regular run - with the year to date of
 * every such run, so its monthly share is what that next run deducts.
 *
 * @param deductedSoFar tax deducted this year: TDS lines on computed, approved and paid runs, plus prior
 *     payroll tax (W-38.2)
 * @param remaining annual tax less {@code deductedSoFar}, never below zero
 * @param monthsLeft months from the later of the effective month and the pay month to March, never below zero
 * @param monthlyShare {@code remaining / monthsLeft} at calculation scale; zero when either is zero
 * @param nextPayMonth the month the share is deducted in; {@code null} when no month of the year is left
 */
public record TdsSpread(
        BigDecimal deductedSoFar, BigDecimal remaining, int monthsLeft, Money monthlyShare, YearMonth nextPayMonth) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    public TdsSpread {
        Objects.requireNonNull(deductedSoFar, "deductedSoFar must not be null");
        Objects.requireNonNull(remaining, "remaining must not be null");
        Objects.requireNonNull(monthlyShare, "monthlyShare must not be null");
    }

    /**
     * The spread for {@code payMonth}.
     *
     * @param annualTax the record's annual tax
     * @param deductedSoFar tax already deducted this financial year
     * @param effectiveFrom the record's effective month
     * @param payMonth the month being paid
     * @param fy the financial year of the record
     */
    public static TdsSpread of(
            BigDecimal annualTax,
            BigDecimal deductedSoFar,
            YearMonth effectiveFrom,
            YearMonth payMonth,
            FinancialYear fy) {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        Objects.requireNonNull(payMonth, "payMonth must not be null");
        Objects.requireNonNull(fy, "fy must not be null");
        BigDecimal deducted = deductedSoFar != null ? deductedSoFar : ZERO;
        BigDecimal remaining = remaining(annualTax, deducted);

        YearMonth startMonth = payMonth.isAfter(effectiveFrom) ? payMonth : effectiveFrom;
        YearMonth march = YearMonth.of(fy.endYear(), 3);
        long months = ChronoUnit.MONTHS.between(startMonth, march) + 1;
        int monthsLeft = months > 0 ? (int) months : 0;

        // As before D-145: a share whenever something remains and a month is left, even one that rounds to 0.
        Money share = remaining.signum() > 0 && monthsLeft > 0
                ? Money.of(remaining).divide(BigDecimal.valueOf(monthsLeft))
                : Money.ZERO;
        return new TdsSpread(deducted, remaining, monthsLeft, share, monthsLeft > 0 ? startMonth : null);
    }

    /** Every month of the year has a counted run: nothing more is deducted this year. */
    public static TdsSpread yearDone(BigDecimal annualTax, BigDecimal deductedSoFar) {
        BigDecimal deducted = deductedSoFar != null ? deductedSoFar : ZERO;
        return new TdsSpread(deducted, remaining(annualTax, deducted), 0, Money.ZERO, null);
    }

    private static BigDecimal remaining(BigDecimal annualTax, BigDecimal deducted) {
        Objects.requireNonNull(annualTax, "annualTax must not be null");
        BigDecimal difference = annualTax.subtract(deducted);
        return difference.signum() < 0 ? ZERO : difference;
    }

    /**
     * Whether a pay run line is written: something remains and a month is left to deduct it in. As origin
     * {@code TaxLineContributor} did, the line is written even when the 4-place share rounds to zero.
     */
    public boolean deducts() {
        return remaining.signum() > 0 && monthsLeft > 0;
    }
}
