package com.infinevo.payroll.payrun;

import java.io.Serial;
import java.util.UUID;

/** Inputs were added to a run that is not an off-cycle run in {@code DRAFT} (W-30.2 §4). Maps to {@code 409}. */
public class NotAnOffCycleRunException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotAnOffCycleRunException(UUID id, PayRunType runType, PayRunStatus status) {
        super("Pay run " + id + " is a " + runType + " run in " + status
                + "; inputs can only be added to an off-cycle run in DRAFT");
    }
}
