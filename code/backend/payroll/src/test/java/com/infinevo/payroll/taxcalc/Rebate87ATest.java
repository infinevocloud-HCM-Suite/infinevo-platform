package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.Rebate87A;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.shared.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Rebate87A} Section 87A rebate calculation (W-33.1 spec § 3 step 5).
 */
class Rebate87ATest {

    private static final Section87aRebateRule NEW_REGIME_2025_2026_REBATE = new Section87aRebateRule(
            "2025-2026",
            "NEW",
            Money.of("1200000"),
            Money.of("60000"),
            true,
            "Section 87A, new regime (Union Budget 2025)");

    private static final Section87aRebateRule OLD_REGIME_CAPPED_REBATE = new Section87aRebateRule(
            "2025-2026", "OLD", Money.of("500000"), Money.of("12500"), false, "Section 87A, old regime");

    @Test
    @DisplayName("taxable <= 12,00,000 with full rebate returns entire taxBeforeRebate")
    void fullRebateAtOrBelowThreshold() {
        Money taxable = Money.of("1200000");
        Money taxBeforeRebate = Money.of("60000");

        Money rebate = Rebate87A.of(taxable, taxBeforeRebate, NEW_REGIME_2025_2026_REBATE);
        assertThat(rebate).isEqualTo(Money.of("60000"));

        Money lowerTaxable = Money.of("1000000");
        Money lowerTax = Money.of("40000");
        Money lowerRebate = Rebate87A.of(lowerTaxable, lowerTax, NEW_REGIME_2025_2026_REBATE);
        assertThat(lowerRebate).isEqualTo(Money.of("40000"));
    }

    @Test
    @DisplayName("taxable > 12,00,000 returns ZERO rebate (threshold is strict)")
    void zeroRebateAboveThreshold() {
        Money taxable = Money.of("1200001");
        Money taxBeforeRebate = Money.of("60000.15");

        Money rebate = Rebate87A.of(taxable, taxBeforeRebate, NEW_REGIME_2025_2026_REBATE);
        assertThat(rebate).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("capped rebate limits to maxRebateAmount when isFullRebate is false")
    void cappedRebateLimitsToMaxAmount() {
        Money taxable = Money.of("500000");
        Money taxBeforeRebate = Money.of("15000");

        Money rebate = Rebate87A.of(taxable, taxBeforeRebate, OLD_REGIME_CAPPED_REBATE);
        assertThat(rebate).isEqualTo(Money.of("12500"));

        Money lowTax = Money.of("8000");
        Money lowTaxRebate = Rebate87A.of(taxable, lowTax, OLD_REGIME_CAPPED_REBATE);
        assertThat(lowTaxRebate).isEqualTo(Money.of("8000"));
    }

    @Test
    @DisplayName("null rule or zero tax returns ZERO")
    void handlesNullOrZeroTax() {
        assertThat(Rebate87A.of(Money.of("1000000"), Money.ZERO, NEW_REGIME_2025_2026_REBATE))
                .isEqualTo(Money.ZERO);
        assertThat(Rebate87A.of(Money.of("1000000"), Money.of("40000"), null)).isEqualTo(Money.ZERO);
    }
}
