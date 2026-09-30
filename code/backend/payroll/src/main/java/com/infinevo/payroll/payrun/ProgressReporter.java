package com.infinevo.payroll.payrun;

/**
 * Told how far a computation has got (W-29.4 §3): every ten employees and on the last. The worker's
 * reporter turns it into {@code JobService.updateProgress}; the run's own counter is written by the
 * computation itself.
 */
@FunctionalInterface
public interface ProgressReporter {

    /** Reports nothing: a caller that only needs the result. */
    ProgressReporter NONE = (done, total) -> {};

    void report(int done, int total);
}
