package com.infinevo.payroll.taxcalc.reader.model;

import com.infinevo.shared.money.Money;
import java.util.Objects;

/**
 * Statutory Section 87A rebate rule from {@code reference.section87a_rebate_rule_master} (W-33.1).
 */
public record Section87aRebateRule(
        String financialYear,
        String regime,
        Money incomeThreshold,
        Money maxRebateAmount,
        boolean isFullRebate,
        String remarks) {

    public Section87aRebateRule {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(incomeThreshold, "incomeThreshold must not be null");
        Objects.requireNonNull(maxRebateAmount, "maxRebateAmount must not be null");
    }
}
