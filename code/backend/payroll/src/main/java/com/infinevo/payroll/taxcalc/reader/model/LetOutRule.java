package com.infinevo.payroll.taxcalc.reader.model;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Statutory let-out property rule row from {@code reference.let_out_property_rule_master} (W-33.2 spec ? 3, ? 4).
 *
 * @param financialYear the financial year label (e.g. "2025-2026")
 * @param taxRegime regime label (e.g. "OLD")
 * @param standardDeductionPercent standard deduction on net annual value (e.g. 30.00)
 * @param maxLossSetoffLimit maximum house property loss set off against salary (e.g. 2,00,000)
 * @param isHomeLoanInterestAllowed whether home loan interest is deductible against let-out income
 * @param isLossCarryForwardAllowed whether excess loss is carried forward
 */
public record LetOutRule(
        String financialYear,
        String taxRegime,
        BigDecimal standardDeductionPercent,
        Money maxLossSetoffLimit,
        boolean isHomeLoanInterestAllowed,
        boolean isLossCarryForwardAllowed) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public LetOutRule {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(taxRegime, "taxRegime must not be null");
        Objects.requireNonNull(standardDeductionPercent, "standardDeductionPercent must not be null");
        Objects.requireNonNull(maxLossSetoffLimit, "maxLossSetoffLimit must not be null");
    }

    /**
     * Returns the standard deduction percentage as a fraction (e.g. 0.30 for 30%).
     */
    public BigDecimal standardDeductionFraction() {
        return standardDeductionPercent.divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }
}
