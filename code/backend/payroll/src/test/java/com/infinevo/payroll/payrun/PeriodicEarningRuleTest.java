package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** W-29.2 §7 — every row of the periodic table in §3, and how frequency text is read. */
class PeriodicEarningRuleTest {

    private static final Money MONTHLY = Money.of("1000.0000");
    private static final Money ANNUAL = Money.of("10000.0000");
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 15);

    @ParameterizedTest(name = "{0} in month {1} pays {2}")
    @CsvSource({
        "MONTHLY,1,1000.0000",
        "MONTHLY,2,1000.0000",
        "MONTHLY,12,1000.0000",
        "QUARTERLY,1,2500.0000",
        "QUARTERLY,4,2500.0000",
        "QUARTERLY,7,2500.0000",
        "QUARTERLY,10,2500.0000",
        "QUARTERLY,2,",
        "QUARTERLY,12,",
        "HALF_YEARLY,1,5000.0000",
        "HALF_YEARLY,7,5000.0000",
        "HALF_YEARLY,4,",
        "HALF_YEARLY,10,",
        "YEARLY,1,10000.0000",
        "YEARLY,7,",
        "YEARLY,12,"
    })
    void paysInItsMonthsOnly(EarningFrequency frequency, int month, String expected) {
        Optional<Money> amount =
                PeriodicEarningRule.amountFor(frequency, MONTHLY, ANNUAL, YearMonth.of(2026, month), LONG_AGO);

        if (expected == null) {
            assertThat(amount).isEmpty();
        } else {
            assertThat(amount).contains(Money.of(expected));
        }
    }

    @Test
    @DisplayName("A joiner two months before July gets no quarterly line; three months does")
    void quarterlyNeedsThreeMonths() {
        YearMonth july = YearMonth.of(2026, 7);
        assertThat(PeriodicEarningRule.amountFor(
                        EarningFrequency.QUARTERLY, MONTHLY, ANNUAL, july, LocalDate.of(2026, 5, 20)))
                .isEmpty();
        assertThat(PeriodicEarningRule.amountFor(
                        EarningFrequency.QUARTERLY, MONTHLY, ANNUAL, july, LocalDate.of(2026, 4, 1)))
                .isPresent();
    }

    @Test
    @DisplayName("Half-yearly needs six months completed, yearly twelve")
    void longerFrequenciesNeedLongerService() {
        assertThat(PeriodicEarningRule.amountFor(
                        EarningFrequency.HALF_YEARLY, MONTHLY, ANNUAL, YearMonth.of(2026, 7), LocalDate.of(2026, 2, 1)))
                .isEmpty();
        assertThat(PeriodicEarningRule.amountFor(
                        EarningFrequency.HALF_YEARLY, MONTHLY, ANNUAL, YearMonth.of(2026, 7), LocalDate.of(2026, 1, 1)))
                .isPresent();
        assertThat(PeriodicEarningRule.amountFor(
                        EarningFrequency.YEARLY, MONTHLY, ANNUAL, YearMonth.of(2027, 1), LocalDate.of(2026, 2, 1)))
                .isEmpty();
        assertThat(PeriodicEarningRule.amountFor(
                        EarningFrequency.YEARLY, MONTHLY, ANNUAL, YearMonth.of(2027, 1), LocalDate.of(2026, 1, 1)))
                .isPresent();
    }

    @Test
    @DisplayName("MONTHLY pays every month — legacy paid zero")
    void monthlyPaysEveryMonth() {
        for (int month = 1; month <= 12; month++) {
            assertThat(PeriodicEarningRule.amountFor(
                            EarningFrequency.MONTHLY, MONTHLY, ANNUAL, YearMonth.of(2026, month), LONG_AGO))
                    .contains(MONTHLY);
        }
    }

    @Test
    @DisplayName("annual_amount / 4 is carried at scale 4, not rounded to 2")
    void quarterlyDivisionKeepsScaleFour() {
        Money amount = PeriodicEarningRule.amountFor(
                        EarningFrequency.QUARTERLY, MONTHLY, Money.of("10000.0100"), YearMonth.of(2026, 1), LONG_AGO)
                .orElseThrow();

        assertThat(amount.raw()).isEqualByComparingTo(new BigDecimal("2500.0025"));
        assertThat(amount.raw().scale()).isEqualTo(4);
    }

    @ParameterizedTest(name = "\"{0}\" reads as {1}")
    @CsvSource({
        "Monthly,MONTHLY", "QUARTERLY,QUARTERLY", "quarterly,QUARTERLY",
        "Half-Yearly,HALF_YEARLY", "HALF_YEARLY,HALF_YEARLY", "half yearly,HALF_YEARLY",
        "Yearly,YEARLY", "ANNUAL,YEARLY", "annually,YEARLY"
    })
    void frequencyTextIsReadByItsWord(String text, EarningFrequency expected) {
        assertThat(EarningFrequency.parse(text)).contains(expected);
    }

    @Test
    @DisplayName("Blank or unrecognised frequency text reads as nothing")
    void unknownFrequencyIsEmpty() {
        assertThat(EarningFrequency.parse(null)).isEmpty();
        assertThat(EarningFrequency.parse(" ")).isEmpty();
        assertThat(EarningFrequency.parse("fortnightly")).isEmpty();
    }
}
