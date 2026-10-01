package com.infinevo.payroll.taxcalc.reader.model;

import com.infinevo.shared.money.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Statutory home loan rule row from {@code reference.home_loan_rule_master} (W-33.2 spec ? 3, ? 4).
 *
 * @param financialYear the financial year label (e.g. "2025-2026")
 * @param sectionCode statutory section code ("24B", "80EE", "80EEA")
 * @param sectionName description of the section
 * @param component principal or interest ("PRINCIPAL", "INTEREST")
 * @param propertyType property occupancy type ("SELF_OCCUPIED", "LET_OUT", "BOTH")
 * @param maxLimit statutory deduction cap, or null if uncapped
 * @param loanSanctionFrom start date of sanction eligibility window, or null
 * @param loanSanctionTo end date of sanction eligibility window, or null
 * @param isFirstTimeBuyer whether first-time buyer status is required
 */
public record HomeLoanRule(
        String financialYear,
        String sectionCode,
        String sectionName,
        String component,
        String propertyType,
        Money maxLimit,
        LocalDate loanSanctionFrom,
        LocalDate loanSanctionTo,
        boolean isFirstTimeBuyer) {

    public HomeLoanRule {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(sectionCode, "sectionCode must not be null");
        Objects.requireNonNull(sectionName, "sectionName must not be null");
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(propertyType, "propertyType must not be null");
    }

    /**
     * Checks if a loan sanctioned date falls within this rule's sanction window.
     */
    public boolean isSanctionDateEligible(LocalDate sanctionedOn) {
        if (sanctionedOn == null) {
            return false;
        }
        if (loanSanctionFrom != null && sanctionedOn.isBefore(loanSanctionFrom)) {
            return false;
        }
        if (loanSanctionTo != null && sanctionedOn.isAfter(loanSanctionTo)) {
            return false;
        }
        return true;
    }
}
