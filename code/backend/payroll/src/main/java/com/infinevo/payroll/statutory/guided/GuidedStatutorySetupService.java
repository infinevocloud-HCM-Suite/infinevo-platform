package com.infinevo.payroll.statutory.guided;

/**
 * Service contract for guided statutory setup wizard (W-93).
 */
public interface GuidedStatutorySetupService {

    /**
     * Inspects tenant statutory state and active operating locations to present questionnaire defaults.
     */
    GuidedStatutoryReadinessResponse getReadiness();

    /**
     * Atomically validates answers and applies EPF, ESI, and PT configurations.
     */
    GuidedStatutorySetupResponse applySetup(GuidedStatutorySetupRequest request);
}
