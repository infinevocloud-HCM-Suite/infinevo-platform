package com.infinevo.payroll.taxcalc.recalc;

import java.util.List;
import java.util.UUID;

/**
 * Keeps a failed tax recalculation visible to the payroll officer and lets them retry it (D-147).
 */
public interface TaxRecalcFailureService {

    /**
     * Records that a recalculation failed, in a transaction of its own: the recalculation's transaction has
     * rolled back, so nothing else of it is left. Never throws; a failure to record is logged.
     */
    void recordFailure(UUID tenantId, UUID employeeId, String financialYear, TaxTrigger trigger, Throwable error);

    /** Open failures in the bound tenant, newest first; {@code financialYear} null means every year. */
    List<TaxRecalcFailureResponse> open(String financialYear);

    /**
     * Runs the failed recalculation again. On success the failure is closed and the new computation is
     * returned; on failure the row's attempts and message are updated and
     * {@link TaxRecalcRetryFailedException} is thrown.
     *
     * @throws TaxRecalcFailureNotFoundException if there is no open failure with this id in the bound tenant
     */
    TaxComputationRecord retry(UUID failureId, UUID officerId);
}
