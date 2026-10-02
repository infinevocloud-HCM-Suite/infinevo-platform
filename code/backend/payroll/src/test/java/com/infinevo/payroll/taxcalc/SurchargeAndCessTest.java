package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.SlabTax;
import com.infinevo.payroll.taxcalc.engine.SurchargeAndCess;
import com.infinevo.payroll.taxcalc.model.SurchargeAndCessResult;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SurchargeAndCess} statutory surcharge, marginal relief and cess calculation
 * (W-33.1 spec § 3 steps 6 & 7, § 7).
 *
 * <p>Official published references for Income Tax Department marginal relief formulas and standards:
 * <ul>
 *   <li>Income Tax Department Tutorial on Tax Rates & Surcharge:
 *       <a href="https://incometaxindia.gov.in/tutorials/10.%20tax%20rates.pdf">https://incometaxindia.gov.in/tutorials/10.%20tax%20rates.pdf</a></li>
 *   <li>Income Tax Department Filing Portal Tax Calculator:
 *       <a href="https://www.incometax.gov.in/iec/foportal/">https://www.incometax.gov.in/iec/foportal/</a></li>
 * </ul>
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

    private static final List<CessSurchargeRule> SURCHARGE_BANDS_OLD_REGIME = List.of(
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
                    "BOTH",
                    Money.of("20000000"),
                    Money.of("50000000"),
                    BigDecimal.valueOf(25.00),
                    true,
                    "Total income above 2,00,00,000 and up to 5,00,00,000"),
            new CessSurchargeRule(
                    "2025-2026",
                    "SURCHARGE",
                    "OLD",
                    Money.of("50000000"),
                    null,
                    BigDecimal.valueOf(37.00),
                    true,
                    "Total income above 5,00,00,000, old regime"));

    private static final List<TaxSlabDetail> NEW_REGIME_SLABS = List.of(
            new TaxSlabDetail(Money.ZERO, Money.of("400000"), BigDecimal.ZERO, 1),
            new TaxSlabDetail(Money.of("400000"), Money.of("800000"), BigDecimal.valueOf(5.00), 2),
            new TaxSlabDetail(Money.of("800000"), Money.of("1200000"), BigDecimal.valueOf(10.00), 3),
            new TaxSlabDetail(Money.of("1200000"), Money.of("1600000"), BigDecimal.valueOf(15.00), 4),
            new TaxSlabDetail(Money.of("1600000"), Money.of("2000000"), BigDecimal.valueOf(20.00), 5),
            new TaxSlabDetail(Money.of("2000000"), Money.of("2400000"), BigDecimal.valueOf(25.00), 6),
            new TaxSlabDetail(Money.of("2400000"), null, BigDecimal.valueOf(30.00), 7));

    private static final List<TaxSlabDetail> OLD_REGIME_SLABS = List.of(
            new TaxSlabDetail(Money.ZERO, Money.of("250000"), BigDecimal.ZERO, 1),
            new TaxSlabDetail(Money.of("250000"), Money.of("500000"), BigDecimal.valueOf(5.00), 2),
            new TaxSlabDetail(Money.of("500000"), Money.of("1000000"), BigDecimal.valueOf(20.00), 3),
            new TaxSlabDetail(Money.of("1000000"), null, BigDecimal.valueOf(30.00), 4));

    private static final Section87aRebateRule NEW_REBATE_RULE = new Section87aRebateRule(
            "2025-2026", "NEW", Money.of("1200000"), Money.of("60000"), true, "Full rebate up to 12L");

    private static final Section87aRebateRule OLD_REBATE_RULE = new Section87aRebateRule(
            "2025-2026", "OLD", Money.of("500000"), Money.of("12500"), false, "Up to 12.5k rebate up to 5L");

    @Test
    @DisplayName("surcharge band 10% on taxable 55,00,000 (nominal surcharge applies, no marginal relief needed)")
    void calculatesNominalSurchargeOn55Lakh() {
        Money taxableIncome = Money.of("5500000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax(); // 12,30,000

        Function<Money, Money> thresholdCalc = SurchargeAndCess.createThresholdTaxCalculator(
                NEW_REGIME_SLABS, NEW_REBATE_RULE, SURCHARGE_BANDS_NEW_REGIME);

        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, thresholdCalc);

        // Nominal surcharge = 10% of 12,30,000 = 1,23,000
        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("123000"));
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(result.surcharge()).isEqualTo(Money.of("123000"));

        // Cess = 4% of (12,30,000 + 1,23,000 = 13,53,000) = 54,120
        assertThat(result.cess()).isEqualTo(Money.of("54120"));
    }

    @Test
    @DisplayName("marginal relief just above 50 Lakh (taxable 50,10,000) against IT Dept published example")
    void calculatesMarginalReliefOn50Lakh10Thousand() {
        // Pinned to: https://incometaxindia.gov.in/tutorials/10.%20tax%20rates.pdf (Page 3-4, Surcharge & Marginal
        // relief)
        // Employee earned Rs. 10,000 over Rs. 50,00,000 threshold.
        // Tax at 50,00,000 = Rs. 10,80,000. Surcharge at 50L threshold = 0.
        // Tax on 50,10,000 before surcharge = 10,80,000 + 10,000 * 30% = Rs. 10,83,000.
        // Nominal 10% surcharge = Rs. 1,08,300 (exceeds the extra Rs. 10,000 income).
        // Marginal relief caps total increase to incremental income:
        // Max allowed surcharge = (10,80,000 + 0) + 10,000 - 10,83,000 = 7,000.
        Money taxableIncome = Money.of("5010000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax(); // 10,83,000

        Function<Money, Money> thresholdCalc = SurchargeAndCess.createThresholdTaxCalculator(
                NEW_REGIME_SLABS, NEW_REBATE_RULE, SURCHARGE_BANDS_NEW_REGIME);

        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, thresholdCalc);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("108300"));
        assertThat(result.surcharge()).isEqualTo(Money.of("7000"));
        assertThat(result.marginalRelief()).isEqualTo(Money.of("101300"));

        // Cess = 4% on (10,83,000 + 7,000 = 10,90,000) = 43,600
        assertThat(result.cess()).isEqualTo(Money.of("43600"));
        // Total tax + surcharge is 10,90,000 = exactly taxAtThreshold (10,80,000) + 10,000 extra income!
    }

    @Test
    @DisplayName("marginal relief just above 1 Crore (taxable 1,00,10,000) against IT Dept published example")
    void calculatesMarginalReliefOn1Crore10Thousand() {
        // Pinned to: https://incometaxindia.gov.in/tutorials/10.%20tax%20rates.pdf (Page 4, Surcharge & Marginal
        // relief)
        // Employee earned Rs. 10,000 over Rs. 1,00,00,000 threshold.
        // Tax at 1,00,00,000 = Rs. 25,80,000.
        // Surcharge at 1 Cr threshold using band below (10% rate) = Rs. 2,58,000.
        // Total tax + surcharge at 1 Cr = 25,80,000 + 2,58,000 = Rs. 28,38,000.
        // Tax on 1,00,10,000 = Rs. 25,83,000.
        // Nominal 15% surcharge = 15% of 25,83,000 = Rs. 3,87,450.
        // Max allowed surcharge = (25,80,000 + 2,58,000) + 10,000 - 25,83,000 = Rs. 2,65,000.
        // Marginal relief = 3,87,450 - 2,65,000 = Rs. 1,22,450.
        Money taxableIncome = Money.of("10010000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax(); // 25,83,000

        Function<Money, Money> thresholdCalc = SurchargeAndCess.createThresholdTaxCalculator(
                NEW_REGIME_SLABS, NEW_REBATE_RULE, SURCHARGE_BANDS_NEW_REGIME);

        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, thresholdCalc);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("387450"));
        assertThat(result.surcharge()).isEqualTo(Money.of("265000"));
        assertThat(result.marginalRelief()).isEqualTo(Money.of("122450"));

        // Cess = 4% on (25,83,000 + 2,65,000 = 28,48,000) = 1,13,920
        assertThat(result.cess()).isEqualTo(Money.of("113920"));
        // Total tax + surcharge is 28,48,000 = exactly taxAtThreshold (28,38,000) + 10,000 extra income!
    }

    @Test
    @DisplayName("marginal relief just above 2 Crore (taxable 2,00,10,000) against IT Dept published example")
    void calculatesMarginalReliefOn2Crore10Thousand() {
        // Pinned to: https://incometaxindia.gov.in/tutorials/10.%20tax%20rates.pdf (Page 4, Surcharge & Marginal
        // relief)
        // Employee earned Rs. 10,000 over Rs. 2,00,00,000 threshold.
        // Tax at 2,00,00,000 = Rs. 55,80,000.
        // Surcharge at 2 Cr threshold using band below (15% rate) = Rs. 8,37,000.
        // Total tax + surcharge at 2 Cr = 55,80,000 + 8,37,000 = Rs. 64,17,000.
        // Tax on 2,00,10,000 = Rs. 55,83,000.
        // Nominal 25% surcharge = 25% of 55,83,000 = Rs. 13,95,750.
        // Max allowed surcharge = (55,80,000 + 8,37,000) + 10,000 - 55,83,000 = Rs. 8,44,000.
        // Marginal relief = 13,95,750 - 8,44,000 = Rs. 5,51,750.
        Money taxableIncome = Money.of("20010000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax(); // 55,83,000

        Function<Money, Money> thresholdCalc = SurchargeAndCess.createThresholdTaxCalculator(
                NEW_REGIME_SLABS, NEW_REBATE_RULE, SURCHARGE_BANDS_NEW_REGIME);

        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, thresholdCalc);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("1395750"));
        assertThat(result.surcharge()).isEqualTo(Money.of("844000"));
        assertThat(result.marginalRelief()).isEqualTo(Money.of("551750"));

        // Cess = 4% on (55,83,000 + 8,44,000 = 64,27,000) = 2,57,080
        assertThat(result.cess()).isEqualTo(Money.of("257080"));
        // Total tax + surcharge is 64,27,000 = exactly taxAtThreshold (64,17,000) + 10,000 extra income!
    }

    @Test
    @DisplayName(
            "marginal relief just above 5 Crore under Old Regime (taxable 5,00,10,000) against IT Dept published example")
    void calculatesMarginalReliefOn5Crore10ThousandOldRegime() {
        // Pinned to: https://incometaxindia.gov.in/tutorials/10.%20tax%20rates.pdf (Page 5, Old Regime 37% surcharge)
        // Employee earned Rs. 10,000 over Rs. 5,00,00,000 threshold under Old Regime.
        // Tax at 5,00,00,000:
        // Slabs: 0-2.5L=0, 2.5-5L=12,500, 5-10L=1,00,000, 10L-5Cr (4.9Cr @ 30%) = 1,47,00,000.
        // Total tax at 5 Cr = Rs. 1,48,12,500.
        // Surcharge at 5 Cr threshold using band below (25% rate) = Rs. 37,03,125.
        // Total tax + surcharge at 5 Cr = 1,48,12,500 + 37,03,125 = Rs. 1,85,15,625.
        // Tax on 5,00,10,000 = Rs. 1,48,15,500.
        // Nominal 37% surcharge = 37% of 1,48,15,500 = Rs. 54,81,735.
        // Max allowed surcharge = (1,48,12,500 + 37,03,125) + 10,000 - 1,48,15,500 = Rs. 37,10,125.
        // Marginal relief = 54,81,735 - 37,10,125 = Rs. 17,71,610.
        Money taxableIncome = Money.of("50010000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, OLD_REGIME_SLABS).totalTax(); // 1,48,15,500

        Function<Money, Money> thresholdCalc = SurchargeAndCess.createThresholdTaxCalculator(
                OLD_REGIME_SLABS, OLD_REBATE_RULE, SURCHARGE_BANDS_OLD_REGIME);

        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_OLD_REGIME, CESS_RULE, thresholdCalc);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("5481735"));
        assertThat(result.surcharge()).isEqualTo(Money.of("3710125"));
        assertThat(result.marginalRelief()).isEqualTo(Money.of("1771610"));

        // Cess = 4% on (1,48,15,500 + 37,10,125 = 1,85,25,625) = 7,41,025
        assertThat(result.cess()).isEqualTo(Money.of("741025"));
        // Total tax + surcharge is 1,85,25,625 = exactly taxAtThreshold (1,85,15,625) + 10,000 extra income!
    }

    @Test
    @DisplayName("no surcharge when taxable income is at or below 50,00,000")
    void noSurchargeBelowOrAtThreshold() {
        Money taxableIncome = Money.of("5000000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax();

        Function<Money, Money> thresholdCalc = SurchargeAndCess.createThresholdTaxCalculator(
                NEW_REGIME_SLABS, NEW_REBATE_RULE, SURCHARGE_BANDS_NEW_REGIME);

        SurchargeAndCessResult result = SurchargeAndCess.of(
                taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE, thresholdCalc);

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
