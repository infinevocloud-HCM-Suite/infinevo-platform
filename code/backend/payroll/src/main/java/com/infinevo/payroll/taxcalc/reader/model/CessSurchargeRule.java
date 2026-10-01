package com.infinevo.payroll.taxcalc.reader.model;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Statutory cess or surcharge band rule from {@code reference.cess_surcharge_rule_master} (W-33.1).
 */
public record CessSurchargeRule(
        String financialYear,
        String ruleType,
        String taxRegime,
        Money incomeFrom,
        Money incomeTo,
        BigDecimal rate,
        boolean isMarginalReliefApplicable,
        String remarks) {

    public CessSurchargeRule {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(ruleType, "ruleType must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
    }

    public boolean isCess() {
        return "CESS".equalsIgnoreCase(ruleType);
    }

    public boolean isSurcharge() {
        return "SURCHARGE".equalsIgnoreCase(ruleType);
    }
}
