package com.infinevo.payroll.statutory.guided;

/**
 * Summary of ESI readiness state (W-93).
 */
public record EsiReadiness(boolean isEnabled, String registrationNumber, boolean includeEmployerInCtc) {}
