package com.infinevo.core.employee.detail;

/**
 * What the bank credit says it is for — {@code core.employee_bank.credit_narration},
 * {@code V260__employee_bank_transfer_type_and_narration.sql}. {@link #OTHER} carries its own text in
 * {@code credit_narration_other}; the database checks both.
 */
public enum CreditNarration {
    SALARY("Salary"),
    CONSULTANT_FEES("Consultant fees"),
    OTHER("Other");

    private final String label;

    CreditNarration(String label) {
        this.label = label;
    }

    /** The words that go in the bank file. */
    public String label() {
        return label;
    }
}
