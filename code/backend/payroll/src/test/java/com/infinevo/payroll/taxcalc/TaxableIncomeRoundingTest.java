package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-147: taxable income is a whole rupee and never above gross total income. */
class TaxableIncomeRoundingTest {

    @Test
    @DisplayName("Half-up when that stays within gross")
    void halfUpWithinGross() {
        assertThat(TaxableIncomeRounding.toRupee(Money.of("100000.50"), Money.of("150000.50"))
                        .compareTo(Money.of("100001")))
                .isZero();
        assertThat(TaxableIncomeRounding.toRupee(Money.of("100000.49"), Money.of("100000.49"))
                        .compareTo(Money.of("100000")))
                .isZero();
    }

    @Test
    @DisplayName("Rounded down when half-up would cross gross")
    void flooredAtGross() {
        Money gross = Money.of("480000.50");
        Money taxable = TaxableIncomeRounding.toRupee(gross, gross);
        assertThat(taxable.compareTo(Money.of("480000"))).isZero();
        assertThat(taxable.compareTo(gross)).isNegative();
    }

    @Test
    @DisplayName("Zero stays zero")
    void zero() {
        assertThat(TaxableIncomeRounding.toRupee(Money.ZERO, Money.ZERO).isZero())
                .isTrue();
    }
}
