package com.infinevo.payroll.taxcalc.reader.model;

import com.infinevo.shared.money.Money;
import java.util.Objects;

/**
 * Statutory standard deduction rule from {@code reference.standard_deduction_rule_master} (W-33.1).
 */
public record StandardDeductionRule(String financialYear, String regime, Money amount, String description) {

    public StandardDeductionRule {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
    }
}
