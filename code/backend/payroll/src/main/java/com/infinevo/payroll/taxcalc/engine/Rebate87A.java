package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.shared.money.Money;
import java.util.Objects;

/**
 * Pure calculator for Section 87A income tax rebate (W-33.1 spec § 3 step 5).
 *
 * <p>Formula from spec § 3:
 * {@code rebate = taxableIncome <= income_threshold ? (is_full_rebate ? taxBeforeRebate : min(taxBeforeRebate, max_rebate_amount)) : 0}
 */
public final class Rebate87A {

    private Rebate87A() {}

    /**
     * Calculates Section 87A tax rebate.
     *
     * @param taxableIncome total net taxable income
     * @param taxBeforeRebate tax calculated before rebate
     * @param rebateRule statutory rebate rule from {@code reference.section87a_rebate_rule_master}, nullable
     * @return the rebate amount in {@link Money}
     */
    public static Money of(Money taxableIncome, Money taxBeforeRebate, Section87aRebateRule rebateRule) {
        Objects.requireNonNull(taxableIncome, "taxableIncome must not be null");
        Objects.requireNonNull(taxBeforeRebate, "taxBeforeRebate must not be null");

        if (rebateRule == null || taxBeforeRebate.isZero() || taxBeforeRebate.isNegative()) {
            return Money.ZERO;
        }

        if (taxableIncome.compareTo(rebateRule.incomeThreshold()) <= 0) {
            if (rebateRule.isFullRebate()) {
                return taxBeforeRebate;
            } else {
                return taxBeforeRebate.compareTo(rebateRule.maxRebateAmount()) < 0
                        ? taxBeforeRebate
                        : rebateRule.maxRebateAmount();
            }
        }

        return Money.ZERO;
    }
}
