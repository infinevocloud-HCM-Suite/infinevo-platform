package com.infinevo.core.payinput;

/** Where an untagged entry for a month would post ({@link PayInputPosting}, D-144). */
public enum PayInputPostingState {
    /** No lock: the entry posts to the month asked for. */
    OPEN,
    /** The month's run is locked or computed: the entry posts there and the run must be recomputed. */
    LOCKED,
    /** The month's run is approved or paid: an entry screen refuses it; {@code postedPeriod} is the first open month. */
    CLOSED
}
