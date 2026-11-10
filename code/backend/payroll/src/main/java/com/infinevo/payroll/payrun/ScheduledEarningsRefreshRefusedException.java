package com.infinevo.payroll.payrun;

import java.util.UUID;

/**
 * "Refresh scheduled earnings" was asked of a run it does not apply to: an off-cycle run (its
 * inputs are tagged to the run, never to the month), or a regular run past {@code DRAFT} - its
 * month's inputs are locked, so a new row would be redirected to the next open month. Mapped to 409.
 */
public class ScheduledEarningsRefreshRefusedException extends RuntimeException {

    public ScheduledEarningsRefreshRefusedException(UUID id, PayRunType runType, PayRunStatus status) {
        super("Pay run " + id + " is a " + runType + " run in " + status
                + "; scheduled earnings can be refreshed only on a regular run that is still a draft");
    }
}
