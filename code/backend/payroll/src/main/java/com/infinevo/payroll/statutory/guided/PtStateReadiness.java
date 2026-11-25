package com.infinevo.payroll.statutory.guided;

import java.time.LocalDate;

/**
 * Summary of Professional Tax readiness state for one operating state (W-93).
 */
public record PtStateReadiness(
        String stateCode,
        String stateName,
        boolean leviesPt,
        String registrationNumber,
        boolean configured,
        LocalDate effectiveFrom) {

    public PtStateReadiness(
            String stateCode, String stateName, boolean leviesPt, String registrationNumber, boolean configured) {
        this(stateCode, stateName, leviesPt, registrationNumber, configured, null);
    }
}
