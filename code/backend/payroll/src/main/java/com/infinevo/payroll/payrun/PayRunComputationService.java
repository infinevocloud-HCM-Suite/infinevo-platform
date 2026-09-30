package com.infinevo.payroll.payrun;

import java.util.UUID;

/**
 * Computes one attempt of a {@code COMPUTING} run: every {@link PayLineContributor} for each included
 * employee the attempt has not computed yet (W-29.2 §3, W-29.4 §3), then {@code COMPUTED} or
 * {@code FAILED}. {@code PayRunService.compute} starts the attempt and queues it; the worker's
 * {@code PayrunQueueListener} is the only production caller.
 */
public interface PayRunComputationService {

    /**
     * @param attempt the attempt the queue message names; rows already at it are skipped (resume)
     * @param actor who asked for the computation, written on the rows and the run
     * @param reporter told every ten employees and on the last
     * @throws PayRunNotFoundException if the run is not in the bound tenant
     * @throws SupersededPayRunJobException if the run is not {@code COMPUTING} at {@code attempt}
     */
    PayRunResponse compute(UUID payrunId, int attempt, String actor, ProgressReporter reporter);
}
