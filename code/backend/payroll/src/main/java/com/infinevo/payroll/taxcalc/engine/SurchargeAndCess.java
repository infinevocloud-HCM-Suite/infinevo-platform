package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.model.SurchargeAndCessResult;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure calculator for income tax surcharge and cess (W-33.1 spec § 3 steps 6 & 7).
 *
 * <p>The band rate applies to tax after rebate in full, with no marginal relief, as legacy
 * (W-33.3 § 2, founder decision 2026-10-02). {@code marginalRelief} is always zero.
 */
public final class SurchargeAndCess {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private SurchargeAndCess() {}

    /**
     * Calculates surcharge and health & education cess.
     *
     * @param taxableIncome total net taxable income
     * @param taxAfterRebate tax calculated after Section 87A rebate
     * @param surchargeBands statutory surcharge bands
     * @param cessRule the statutory health & education cess rule (e.g. 4%)
     * @return {@link SurchargeAndCessResult} with full breakdown
     */
    public static SurchargeAndCessResult of(
            Money taxableIncome,
            Money taxAfterRebate,
            List<CessSurchargeRule> surchargeBands,
            CessSurchargeRule cessRule) {

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
        Money surcharge = taxAfterRebate.multiply(rateFactor);
        Money cess = calculateCess(taxAfterRebate.add(surcharge), cessRule);

        return new SurchargeAndCessResult(surcharge, Money.ZERO, surcharge, cess);
    }

    private static Money calculateCess(Money base, CessSurchargeRule cessRule) {
        if (base == null || base.isZero() || base.isNegative() || cessRule == null) {
            return Money.ZERO;
        }
        BigDecimal rateFactor = cessRule.rate().divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
        return base.multiply(rateFactor);
    }
}
