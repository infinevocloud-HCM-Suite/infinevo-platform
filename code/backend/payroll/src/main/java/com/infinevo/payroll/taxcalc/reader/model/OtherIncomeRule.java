package com.infinevo.payroll.taxcalc.reader.model;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Statutory other income rule row from {@code reference.other_income_rule_master} (W-33.2 spec § 3, § 4).
 *
 * @param financialYear the financial year label (e.g. "2025-2026")
 * @param sectionCode statutory section code (e.g. "80TTA", "80TTB")
 * @param sectionName description of the section
 * @param ruleType rule type ("DEDUCTION", "INCOME")
 * @param taxRegime regime eligibility ("OLD", "NEW", "BOTH")
 * @param maxLimit statutory deduction limit, or null if uncapped
 * @param deductionPercent percentage of income deductible (e.g. 100.00)
 * @param isProofRequired whether proof is required
 * @param isConditional whether additional conditions apply (e.g. senior citizen for 80TTB)
 */
public record OtherIncomeRule(
        String financialYear,
        String sectionCode,
        String sectionName,
        String ruleType,
        String taxRegime,
        Money maxLimit,
        BigDecimal deductionPercent,
        boolean isProofRequired,
        boolean isConditional) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public OtherIncomeRule {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(sectionCode, "sectionCode must not be null");
        Objects.requireNonNull(sectionName, "sectionName must not be null");
        Objects.requireNonNull(ruleType, "ruleType must not be null");
        Objects.requireNonNull(taxRegime, "taxRegime must not be null");
    }

    /**
     * Returns the deduction percentage as a fraction (e.g. 1.00 for 100%).
     */
    public BigDecimal deductionFraction() {
        if (deductionPercent == null) {
            return BigDecimal.ONE;
        }
        return deductionPercent.divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }
}
