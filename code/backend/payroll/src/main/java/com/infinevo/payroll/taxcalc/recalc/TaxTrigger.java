package com.infinevo.payroll.taxcalc.recalc;

/**
 * Triggers that cause an annual income tax recalculation and generate an audit record (W-33.3).
 */
public enum TaxTrigger {
    DECLARATION_SUBMITTED,
    SALARY_REVISION,
    SALARY_DEFAULT,
    PROOF_VERIFIED,
    OFFICER
}
