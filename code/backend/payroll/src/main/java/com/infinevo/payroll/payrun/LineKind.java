package com.infinevo.payroll.payrun;

/**
 * What a pay line is (W-29.2 §6). The kind carries the sign; every amount is non-negative.
 * {@link #BENEFIT} is the employer's side of the structure — reported, never paid (BUG-013).
 */
public enum LineKind {
    EARNING,
    DEDUCTION,
    BENEFIT,
    REIMBURSEMENT
}
