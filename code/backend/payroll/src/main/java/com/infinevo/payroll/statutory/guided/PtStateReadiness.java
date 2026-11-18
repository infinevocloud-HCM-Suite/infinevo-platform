package com.infinevo.payroll.statutory.guided;

/**
 * Summary of Professional Tax readiness state for one operating state (W-93).
 */
public record PtStateReadiness(
        String stateCode, String stateName, boolean leviesPt, String registrationNumber, boolean configured) {}
