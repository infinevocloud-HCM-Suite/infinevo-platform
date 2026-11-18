package com.infinevo.payroll.statutory.guided;

/**
 * Summary of EPF readiness state (W-93).
 */
public record EpfReadiness(
        boolean isEnabled,
        String registrationNumber,
        boolean restrictEmployeeToCeiling,
        boolean includeEmployerInCtc) {}
