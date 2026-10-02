package com.infinevo.payroll.proof;

/** Life cycle of one employee's proof of investment for a financial year (W-34.1). */
public enum ProofStatus {
    /** Being filled in by the employee. */
    DRAFT,
    /** Submitted; under review (W-34.2). */
    SUBMITTED,
    /** Reviewed and accepted (W-34.2). */
    APPROVED,
    /** Returned to the employee with a reason (W-34.2); editable and resubmittable. */
    REJECTED;

    /** The employee may change items and files only in these two states. */
    public boolean isEditableState() {
        return this == DRAFT || this == REJECTED;
    }
}
