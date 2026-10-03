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

        SurchargeAndCessResult result =
                SurchargeAndCess.of(taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE);

        // Nominal surcharge = 10% of 12,30,000 = 1,23,000
        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("123000"));
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(result.surcharge()).isEqualTo(Money.of("123000"));

        // Cess = 4% of (12,30,000 + 1,23,000 = 13,53,000) = 54,120
        assertThat(result.cess()).isEqualTo(Money.of("54120"));
    }

    @Test
    @DisplayName("full 10% surcharge just above 50 Lakh (taxable 50,10,000), no marginal relief (W-33.3)")
    void appliesFullSurchargeOn50Lakh10Thousand() {
        Money taxableIncome = Money.of("5010000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax(); // 10,83,000

        SurchargeAndCessResult result =
                SurchargeAndCess.of(taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("108300"));
        assertThat(result.surcharge()).isEqualTo(Money.of("108300"));
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);

        // Legacy: band rate on tax after rebate in full; cess 4% of (10,83,000 + 1,08,300)
        assertThat(result.cess()).isEqualTo(Money.of("47652"));
    }

    @Test
    @DisplayName("full 15% surcharge just above 1 Crore (taxable 1,00,10,000), no marginal relief (W-33.3)")
    void appliesFullSurchargeOn1Crore10Thousand() {
        Money taxableIncome = Money.of("10010000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax(); // 25,83,000

        SurchargeAndCessResult result =
                SurchargeAndCess.of(taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("387450"));
        assertThat(result.surcharge()).isEqualTo(Money.of("387450"));
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);

        // cess 4% of (25,83,000 + 3,87,450)
        assertThat(result.cess()).isEqualTo(Money.of("118818"));
    }

    @Test
    @DisplayName("full 25% surcharge just above 2 Crore (taxable 2,00,10,000), no marginal relief (W-33.3)")
    void appliesFullSurchargeOn2Crore10Thousand() {
        Money taxableIncome = Money.of("20010000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax(); // 55,83,000

        SurchargeAndCessResult result =
                SurchargeAndCess.of(taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("1395750"));
        assertThat(result.surcharge()).isEqualTo(Money.of("1395750"));
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);

        // cess 4% of (55,83,000 + 13,95,750)
        assertThat(result.cess()).isEqualTo(Money.of("279150"));
    }

    @Test
    @DisplayName("full 37% surcharge just above 5 Crore, Old Regime (taxable 5,00,10,000), no marginal relief (W-33.3)")
    void appliesFullSurchargeOn5Crore10ThousandOldRegime() {
        Money taxableIncome = Money.of("50010000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, OLD_REGIME_SLABS).totalTax(); // 1,48,15,500

        SurchargeAndCessResult result =
                SurchargeAndCess.of(taxableIncome, taxAfterRebate, SURCHARGE_BANDS_OLD_REGIME, CESS_RULE);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.of("5481735"));
        assertThat(result.surcharge()).isEqualTo(Money.of("5481735"));
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);

        // cess 4% of (1,48,15,500 + 54,81,735)
        assertThat(result.cess()).isEqualTo(Money.of("811889.40"));
    }

    @Test
    @DisplayName("no surcharge when taxable income is at or below 50,00,000")
    void noSurchargeBelowOrAtThreshold() {
        Money taxableIncome = Money.of("5000000");
        Money taxAfterRebate = SlabTax.of(taxableIncome, NEW_REGIME_SLABS).totalTax();

        SurchargeAndCessResult result =
                SurchargeAndCess.of(taxableIncome, taxAfterRebate, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.ZERO);
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(result.surcharge()).isEqualTo(Money.ZERO);

        // Cess = 4% on 10,80,000 = 43,200
        assertThat(result.cess()).isEqualTo(Money.of("43200"));
    }

    @Test
    @DisplayName("zero tax results in zero surcharge and zero cess")
    void zeroTaxResultsInZeroSurchargeAndCess() {
        SurchargeAndCessResult result =
                SurchargeAndCess.of(Money.of("1200000"), Money.ZERO, SURCHARGE_BANDS_NEW_REGIME, CESS_RULE);

        assertThat(result.nominalSurcharge()).isEqualTo(Money.ZERO);
        assertThat(result.marginalRelief()).isEqualTo(Money.ZERO);
        assertThat(result.surcharge()).isEqualTo(Money.ZERO);
        assertThat(result.cess()).isEqualTo(Money.ZERO);
    }
}
