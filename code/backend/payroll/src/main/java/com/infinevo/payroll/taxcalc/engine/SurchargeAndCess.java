package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.model.SlabTaxResult;
import com.infinevo.payroll.taxcalc.model.SurchargeAndCessResult;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Pure calculator for income tax surcharge with statutory marginal relief and cess (W-33.1 spec § 3 steps 6 & 7).
 *
 * <p>High-income surcharge applies when taxable income exceeds the lowest surcharge band floor
 * (e.g. ₹50,00,000). Marginal relief ensures that tax plus surcharge does not exceed the tax payable
 * on the threshold income plus the incremental income earned above that threshold.
 */
public final class SurchargeAndCess {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private SurchargeAndCess() {}

    /**
     * Creates a shared threshold tax calculator that calculates:
     * (slab tax - 87A rebate) + surcharge at threshold using the band below.
     *
     * @param slabs statutory tax slabs
     * @param rebateRule Section 87A rebate rule
     * @param surchargeBands statutory surcharge bands
     * @return Function computing total tax plus surcharge at the threshold
     */
    public static Function<Money, Money> createThresholdTaxCalculator(
            List<TaxSlabDetail> slabs, Section87aRebateRule rebateRule, List<CessSurchargeRule> surchargeBands) {
        return threshold -> {
            if (threshold == null || threshold.isZero()) {
                return Money.ZERO;
            }
            SlabTaxResult thSlab = SlabTax.of(threshold, slabs);
            Money thRebate = rebateRule != null ? Rebate87A.of(threshold, thSlab.totalTax(), rebateRule) : Money.ZERO;
            Money thTaxAfterRebate = thSlab.totalTax().subtract(thRebate);
            if (thTaxAfterRebate.isNegative()) {
                thTaxAfterRebate = Money.ZERO;
            }

            BigDecimal surchargeRateAtThreshold = BigDecimal.ZERO;
            if (surchargeBands != null) {
                for (CessSurchargeRule band : surchargeBands) {
                    if (band.isSurcharge()
                            && band.incomeTo() != null
                            && band.incomeTo().compareTo(threshold) == 0) {
                        surchargeRateAtThreshold = band.rate();
                        break;
                    }
                }
            }

            Money surchargeAtThreshold = Money.ZERO;
            if (surchargeRateAtThreshold.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rateFactor = surchargeRateAtThreshold.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
                surchargeAtThreshold = thTaxAfterRebate.multiply(rateFactor);
            }

            return thTaxAfterRebate.add(surchargeAtThreshold);
        };
    }

    /**
     * Calculates surcharge with marginal relief and health & education cess.
     *
     * @param taxableIncome total net taxable income
     * @param taxAfterRebate tax calculated after Section 87A rebate
     * @param surchargeBands statutory surcharge bands ordered by income floor
     * @param cessRule the statutory health & education cess rule (e.g. 4%)
     * @param taxAtThresholdCalculator function to compute tax plus surcharge at threshold for marginal relief
     * @return {@link SurchargeAndCessResult} with full breakdown
     */
    public static SurchargeAndCessResult of(
            Money taxableIncome,
            Money taxAfterRebate,
            List<CessSurchargeRule> surchargeBands,
            CessSurchargeRule cessRule,
            Function<Money, Money> taxAtThresholdCalculator) {

        Objects.requireNonNull(taxableIncome, "taxableIncome must not be null");
        Objects.requireNonNull(taxAfterRebate, "taxAfterRebate must not be null");

        if (taxAfterRebate.isNegative()
                || taxAfterRebate.isZero()
                || surchargeBands == null
                || surchargeBands.isEmpty()) {
            Money cess = calculateCess(taxAfterRebate, cessRule);
            return new SurchargeAndCessResult(Money.ZERO, Money.ZERO, Money.ZERO, cess);
        }

        // Find the applicable surcharge band: income_from < taxableIncome <= income_to (or income_to NULL)
        CessSurchargeRule matchingBand = null;
        for (CessSurchargeRule band : surchargeBands.stream()
                .filter(CessSurchargeRule::isSurcharge)
                .sorted(Comparator.comparing(CessSurchargeRule::incomeFrom))
                .toList()) {

            if (band.incomeFrom() != null && taxableIncome.compareTo(band.incomeFrom()) > 0) {
                if (band.incomeTo() == null || taxableIncome.compareTo(band.incomeTo()) <= 0) {
                    matchingBand = band;
                    break;
                }
            }
        }

        if (matchingBand == null) {
            Money cess = calculateCess(taxAfterRebate, cessRule);
            return new SurchargeAndCessResult(Money.ZERO, Money.ZERO, Money.ZERO, cess);
        }

        BigDecimal rateFactor = matchingBand.rate().divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
        Money nominalSurcharge = taxAfterRebate.multiply(rateFactor);
        Money netSurcharge = nominalSurcharge;
        Money marginalRelief = Money.ZERO;

        if (matchingBand.isMarginalReliefApplicable()
                && taxAtThresholdCalculator != null
                && matchingBand.incomeFrom() != null) {
            Money threshold = matchingBand.incomeFrom();
            Money taxAndSurchargeAtThreshold = taxAtThresholdCalculator.apply(threshold);
            if (taxAndSurchargeAtThreshold == null) {
                taxAndSurchargeAtThreshold = Money.ZERO;
            }

            Money extraIncome = taxableIncome.subtract(threshold);
            // surcharge <= (taxAtThreshold + surchargeAtThreshold) + (income - threshold) - taxAfterRebate
            Money maxAllowedSurcharge =
                    taxAndSurchargeAtThreshold.add(extraIncome).subtract(taxAfterRebate);

            if (maxAllowedSurcharge.isNegative()) {
                maxAllowedSurcharge = Money.ZERO;
            }

            if (nominalSurcharge.compareTo(maxAllowedSurcharge) > 0) {
                netSurcharge = maxAllowedSurcharge;
                marginalRelief = nominalSurcharge.subtract(netSurcharge);
            }
        }

        Money baseForCess = taxAfterRebate.add(netSurcharge);
        Money cess = calculateCess(baseForCess, cessRule);

        return new SurchargeAndCessResult(nominalSurcharge, marginalRelief, netSurcharge, cess);
    }

    private static Money calculateCess(Money base, CessSurchargeRule cessRule) {
        if (base == null || base.isZero() || base.isNegative() || cessRule == null) {
            return Money.ZERO;
        }
        BigDecimal rateFactor = cessRule.rate().divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
        return base.multiply(rateFactor);
    }
}
