package com.infinevo.payroll.deduction;

/**
 * W-35.2 §13 decision 3: {@code POSTED} on entry, {@code REVERSED} once its ledger row is reversed.
 * Legacy's {@code ACTIVE} / {@code INPAYRUN} / {@code PROCESSED} ({@code DeductionStatus.java:3-7})
 * were the pay run's bookkeeping; the period lock is that now.
 */
public enum DeductionState {
    POSTED,
    REVERSED
}
