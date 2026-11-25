package com.infinevo.payroll.gratuity;

/**
 * Exit reason used to evaluate gratuity eligibility under the Payment of Gratuity Act, 1972 (W-91).
 */
public enum GratuityReason {
    RESIGNATION,
    RETIREMENT,
    DEATH,
    DISABLEMENT;

    /**
     * Death and disablement waive the 5-year continuous service requirement.
     */
    public boolean isExemptFrom5YrRule() {
        return this == DEATH || this == DISABLEMENT;
    }
}
