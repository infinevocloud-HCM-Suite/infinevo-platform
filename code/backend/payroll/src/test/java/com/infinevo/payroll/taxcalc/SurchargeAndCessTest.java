package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.SurchargeAndCess;
import com.infinevo.payroll.taxcalc.model.SurchargeAndCessResult;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SurchargeAndCess} statutory surcharge, marginal relief and cess calculation
 * (W-33.1 spec § 3 steps 6 & 7, § 7).
 *
 * <p>Official reference for marginal relief formula:
 * Income Tax Department, Government of India: https://incometaxindia.gov.in/
 */
class SurchargeAndCessTest {

    private static final CessSurchargeRule CESS_RULE = new CessSurchargeRule(
            "2025-2026", "CESS", "BOTH", null, null, BigDecimal.valueOf(4.00), false, "Health and education cess");

    private static final List<CessSurchargeRule> SURCHARGE_BANDS_NEW_REGIME = List.of(
            new CessSurchargeRule(
                    "2025-2026",
                    "SURCHARGE",
                    "BOTH",
                    Money.of("5000000"),
                    Money.of("10000000"),
                    BigDecimal.valueOf(10.00),
                    true,
                    "Total income above 50,00,000 and up to 1,00,00,000"),
            new CessSurchargeRule(
                    "2025-2026",
                    "SURCHARGE",
                    "BOTH",
                    Money.of("10000000"),
                    Money.of("20000000"),
                    BigDecimal.valueOf(15.00),
                    true,
                    "Total income above 1,00,00,000 and up to 2,00,00,000"),
            new CessSurchargeRule(
                    "2025-2026",
                    "SURCHARGE",
                    "NEW",
                    Money.of("20000000"),
                    null,
                    BigDecimal.valueOf(25.00),
                    true,
                    "Total income above 2,00,00,000, new regime — capped at 25%"));

    @Test
    @DisplayName("surcharge band 10% on taxable 55,00,000 (nominal surcharge applies, no marginal relief needed)")
    void calculatesNominalSurchargeOn55Lakh() {
        Money taxableIncome = Money.of("5500000");
        Money taxAfterRebate = Money.of("1230000"); // 10,80,000 on 50L + 5L * 30% = 12,30,000

        // At 50L threshold, tax is 10,80,000
        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, threshold -> Money.of("1080000"));

        // Nominal surcharge = 10% of 12,30,000 = 1,23,000
        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("123000"));
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(result.surcharge()).isEqualTo(Money.of("123000"));

        // Cess = 4% of (12,30,000 + 1,23,000 = 13,53,000) = 54,120
        assertThat(result.cess()).isEqualTo(Money.of("54120"));
    }

    @Test
    @DisplayName("marginal relief on taxable 50,10,000 against Income Tax Department published standard")
    void calculatesMarginalReliefOn50Lakh10Thousand() {
        // Employee earned Rs. 10,000 over Rs. 50,00,000 threshold.
        // Tax at 50,00,000 = Rs. 10,80,000.
        // Tax on 50,10,000 before surcharge = 10,80,000 + 10,000 * 30% = Rs. 10,83,000.
        // Nominal 10% surcharge = Rs. 1,08,300 (which exceeds the extra Rs. 10,000 income!).
        // Marginal relief caps total increase to incremental income:
        // Extra income = 10,000; Extra tax before surcharge = 3,000; Max surcharge = 7,000.
        Money taxableIncome = Money.of("5010000");
        Money taxAfterRebate = Money.of("1083000");

        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, threshold -> Money.of("1080000"));

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("108300"));
        assertThat(result.surcharge()).isEqualTo(Money.of("7000"));
        assertThat(result.marginalRelief()).isEqualTo(Money.of("101300"));

        // Cess = 4% on (10,83,000 + 7,000 = 10,90,000) = 43,600
        assertThat(result.cess()).isEqualTo(Money.of("43600"));

        // Notice: Total tax + surcharge is 10,90,000, which is exactly taxAtThreshold (10,80,000) + 10,000 extra
        // income!
    }

    @Test
    @DisplayName("no surcharge when taxable income is at or below 50,00,000")
    void noSurchargeBelowOrAtThreshold() {
        Money taxableIncome = Money.of("5000000");
        Money taxAfterRebate = Money.of("1080000");

        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, threshold -> Money.of("1080000"));

        assertThat(result.nominalSurcharge()).isEqualTo(Money.ZERO);
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(result.surcharge()).isEqualTo(Money.ZERO);

        // Cess = 4% on 10,80,000 = 43,200
        assertThat(result.cess()).isEqualTo(Money.of("43200"));
    }

    @Test
    @DisplayName("zero tax results in zero surcharge and zero cess")
    void zeroTaxResultsInZeroSurchargeAndCess() {
        SurchargeAndCessResult result = SurchargeAndCess.of(
                Money.of("1200000"), Money.ZERO, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, threshold -> Money.ZERO);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.ZERO);
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(result.surcharge()).isEqualTo(Money.ZERO);
        assertThat(result.cess()).isEqualTo(Money.ZERO);
    }
}
