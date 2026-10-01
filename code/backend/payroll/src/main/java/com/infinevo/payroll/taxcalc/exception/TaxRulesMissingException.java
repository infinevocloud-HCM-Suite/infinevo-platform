package com.infinevo.payroll.taxcalc.exception;

/**
 * Thrown when statutory income tax rules are missing for a financial year (W-33.1 spec ? 4, ? 9).
 *
 * <p>Maps to HTTP 422 Unprocessable Entity with reason code {@code TAX_RULES_MISSING} and names the missing table.
 */
public class TaxRulesMissingException extends RuntimeException {

    private final String tableName;
    private final String financialYear;

    public TaxRulesMissingException(String tableName, String financialYear) {
        super(String.format(
                "Required statutory tax rules missing from %s for financial year %s", tableName, financialYear));
        this.tableName = tableName;
        this.financialYear = financialYear;
    }

    public String tableName() {
        return tableName;
    }

    public String ruleTable() {
        return tableName;
    }

    public String getRuleTable() {
        return tableName;
    }

    public String financialYear() {
        return financialYear;
    }

    public String getFinancialYear() {
        return financialYear;
    }
}
