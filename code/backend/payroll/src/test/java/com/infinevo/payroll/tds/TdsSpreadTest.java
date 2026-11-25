package com.infinevo.payroll.tds;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-145: the one spread of annual tax over the months left, shared by the pay run line and the officer's
 * TDS figures. The hand figures are the W-36.1 §7 ones {@link TaxLineContributorTest} checks on the line.
 */
class TdsSpreadTest {

    private static final FinancialYear FY = FinancialYear.parse("2026-2027");

    @Test
    @DisplayName("April, 120,000 annual, nothing deducted: 12 months left, 10,000.0000 a month")
    void aprilFullYear() {
        TdsSpread spread = TdsSpread.of(
                new BigDecimal("120000.00"), BigDecimal.ZERO, YearMonth.of(2026, 4), YearMonth.of(2026, 4), FY);

        assertThat(spread.deductedSoFar()).isEqualByComparingTo("0");
        assertThat(spread.remaining()).isEqualByComparingTo("120000");
        assertThat(spread.monthsLeft()).isEqualTo(12);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("10000.0000");
        assertThat(spread.deducts()).isTrue();
    }

    @Test
    @DisplayName("October, 70,000 deducted of 120,000: 6 months left, 8,333.3333 a month")
    void octoberWithDeductions() {
        TdsSpread spread = TdsSpread.of(
                new BigDecimal("120000.00"),
                new BigDecimal("70000.0000"),
                YearMonth.of(2026, 4),
                YearMonth.of(2026, 10),
                FY);

        assertThat(spread.deductedSoFar()).isEqualByComparingTo("70000");
        assertThat(spread.remaining()).isEqualByComparingTo("50000");
        assertThat(spread.monthsLeft()).isEqualTo(6);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("8333.3333");
        assertThat(spread.monthlyShare().toAmount()).isEqualByComparingTo("8333.33");
    }

    @Test
    @DisplayName("An effective month after the pay month counts months from the effective month")
    void effectiveMonthLaterThanPayMonth() {
        TdsSpread spread = TdsSpread.of(
                new BigDecimal("60000.00"), BigDecimal.ZERO, YearMonth.of(2026, 10), YearMonth.of(2026, 5), FY);

        assertThat(spread.monthsLeft()).isEqualTo(6);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("10000.0000");
    }

    @Test
    @DisplayName("Deducted beyond the annual tax: remaining is zero, not negative, and nothing is deducted")
    void overDeducted() {
        TdsSpread spread = TdsSpread.of(
                new BigDecimal("120000.00"),
                new BigDecimal("125000.0000"),
                YearMonth.of(2026, 4),
                YearMonth.of(2026, 11),
                FY);

        assertThat(spread.remaining()).isEqualByComparingTo("0");
        assertThat(spread.monthsLeft()).isEqualTo(5);
        assertThat(spread.monthlyShare().isZero()).isTrue();
        assertThat(spread.deducts()).isFalse();
    }

    @Test
    @DisplayName("A pay month after March of the year leaves no months and no share")
    void afterTheYear() {
        TdsSpread spread = TdsSpread.of(
                new BigDecimal("120000.00"), BigDecimal.ZERO, YearMonth.of(2026, 4), YearMonth.of(2027, 5), FY);

        assertThat(spread.monthsLeft()).isZero();
        assertThat(spread.remaining()).isEqualByComparingTo("120000");
        assertThat(spread.deducts()).isFalse();
    }

    @Test
    @DisplayName("March with 1,234.50 remaining: one month left, the whole remainder")
    void march() {
        TdsSpread spread =
                TdsSpread.of(new BigDecimal("1234.50"), null, YearMonth.of(2026, 4), YearMonth.of(2027, 3), FY);

        assertThat(spread.deductedSoFar()).isEqualByComparingTo("0");
        assertThat(spread.monthsLeft()).isEqualTo(1);
        assertThat(spread.monthlyShare().raw()).isEqualByComparingTo("1234.5000");
    }

    @Test
    @DisplayName("A remainder whose 4-place share rounds to 0 still deducts, as the pay run did before D-145")
    void tinyRemainderStillDeducts() {
        TdsSpread spread = TdsSpread.of(
                new BigDecimal("120000.00"),
                new BigDecimal("119999.9999"),
                YearMonth.of(2026, 4),
                YearMonth.of(2026, 4),
                FY);

        assertThat(spread.remaining()).isEqualByComparingTo("0.0001");
        assertThat(spread.monthsLeft()).isEqualTo(12);
        assertThat(spread.monthlyShare().isZero()).isTrue();
        assertThat(spread.deducts()).isTrue();
        assertThat(spread.nextPayMonth()).isEqualTo(YearMonth.of(2026, 4));
    }

    @Test
    @DisplayName("Year done: remaining kept, nothing left to deduct and no next pay month")
    void yearDone() {
        TdsSpread spread = TdsSpread.yearDone(new BigDecimal("120000.00"), new BigDecimal("100000.0000"));

        assertThat(spread.remaining()).isEqualByComparingTo("20000");
        assertThat(spread.monthsLeft()).isZero();
        assertThat(spread.deducts()).isFalse();
        assertThat(spread.nextPayMonth()).isNull();
    }
}
