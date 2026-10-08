package com.infinevo.payroll.scheduled;

/**
 * Where a scheduled earning stands (W-73.6 §2). Stored as the string name in
 * {@code payroll.scheduled_earning.status} ({@code V161}), never as an ordinal.
 */
public enum ScheduledEarningStatus {
    /** Waiting for its next period; the only state materialisation reads. */
    SCHEDULED,
    /** Held by an officer; no instalment is written until resumed. */
    PAUSED,
    /** Stopped for good — by an officer, or by the employee's termination. Terminal. */
    CANCELLED,
    /** Every instalment has become a pay input. Terminal. */
    PAID;

    /** True when no further instalment can ever be written from this state. */
    public boolean isTerminal() {
        return this == CANCELLED || this == PAID;
    }
}
