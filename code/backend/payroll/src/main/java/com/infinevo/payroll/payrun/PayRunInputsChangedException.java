package com.infinevo.payroll.payrun;

import java.io.Serial;

/**
 * {@code POST /approve} on a regular run whose month took entries after it was computed (D-144). A
 * locked or computed month still accepts entries, and the founder's rule is that the run must then
 * be recomputed; approving would close the month with those entries unpaid. Maps to {@code 409}.
 */
public class PayRunInputsChangedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long count;

    public PayRunInputsChangedException(long count) {
        super(message(count));
        this.count = count;
    }

    /** "3 new pay inputs since this run was computed. Compute again before approving." */
    public static String message(long count) {
        return count + (count == 1 ? " new pay input" : " new pay inputs")
                + " since this run was computed. Compute again before approving.";
    }

    public long count() {
        return count;
    }
}
