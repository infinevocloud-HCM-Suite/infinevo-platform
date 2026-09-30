package com.infinevo.payroll.payrun;

import java.io.Serial;
import java.time.YearMonth;

/** A non-cancelled run already exists for the tenant and period. Maps to {@code 409}. */
public class DuplicatePayRunException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicatePayRunException(YearMonth period) {
        super("A pay run for " + period + " already exists; cancel it before creating another");
    }
}
