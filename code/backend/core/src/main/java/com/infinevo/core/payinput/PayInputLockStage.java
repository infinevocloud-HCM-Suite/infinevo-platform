package com.infinevo.core.payinput;

/**
 * The stage of a period lock ({@code core.pay_input_period_lock.stage}, {@code V220}, D-144).
 * Founder 2026-11-23: a month whose run is locked or computed still takes entries, which the run
 * must then pick up by recomputing; a month whose run is approved (or paid) takes none.
 */
public enum PayInputLockStage {
    /** Run locked or computed. An untagged entry still posts to this month; the run must be recomputed. */
    LOCKED,
    /** Run approved or paid. An untagged entry for this month is refused by the entry screens. */
    CLOSED
}
