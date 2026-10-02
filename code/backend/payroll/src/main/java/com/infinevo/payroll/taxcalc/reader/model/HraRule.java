package com.infinevo.payroll.taxcalc.reader.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Statutory HRA exemption rule row from {@code reference.hra_rule_master} (W-33.2 spec § 3 step 2).
 *
 * @param financialYear the financial year label (e.g. "2025-2026")
 * @param basicDaPercentThreshold percentage of basic+DA; rent below this gives zero exemption
 * @param metroPercent percentage of basic+DA for the metro HRA cap
 * @param nonMetroPercent percentage of basic+DA for the non-metro HRA cap
 */
public record HraRule(
        String financialYear, BigDecimal basicDaPercentThreshold, BigDecimal metroPercent, BigDecimal nonMetroPercent) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public HraRule {
        if (financialYear == null || financialYear.isBlank()) {
            throw new IllegalArgumentException("financialYear must not be blank");
        }
        if (basicDaPercentThreshold == null || basicDaPercentThreshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("basicDaPercentThreshold must not be null or negative");
        }
        if (metroPercent == null || metroPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("metroPercent must not be null or negative");
        }
        if (nonMetroPercent == null || nonMetroPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("nonMetroPercent must not be null or negative");
        }
    }

    /**
     * Returns the applicable city percentage as a fraction (e.g. 0.50 for metro with 50% rule).
     */
    public BigDecimal cityFraction(boolean isMetro) {
        BigDecimal pct = isMetro ? metroPercent : nonMetroPercent;
        return pct.divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }

    /**
     * Returns the basic-DA threshold as a fraction (e.g. 0.10 for 10%).
     */
    public BigDecimal thresholdFraction() {
        return basicDaPercentThreshold.divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }
}
