package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.payroll.payrun.PayRunStatus;
import java.io.Serial;

/**
 * Thrown when bank salary file generation is requested for a pay run that is neither APPROVED nor PAID (W-88).
 * Maps to {@code 409 Conflict}.
 */
public class BankFileInvalidStatusException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BankFileInvalidStatusException(PayRunStatus currentStatus) {
        super("Bank file can only be generated for APPROVED or PAID pay runs (current status: " + currentStatus + ")");
    }
}
