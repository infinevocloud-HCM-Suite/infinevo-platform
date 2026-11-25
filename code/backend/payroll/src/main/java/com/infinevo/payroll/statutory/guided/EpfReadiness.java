package com.infinevo.payroll.statutory.guided;

import java.time.LocalDate;

/**
 * Summary of EPF readiness state (W-93).
 */
public record EpfReadiness(
        boolean isEnabled,
        String registrationNumber,
        LocalDate registrationDate,
        boolean restrictEmployeeToCeiling,
        boolean includeEmployerInCtc,
        boolean includeEdliInCtc,
        boolean includeAdminInCtc) {

    public EpfReadiness(
            boolean isEnabled,
            String registrationNumber,
            boolean restrictEmployeeToCeiling,
            boolean includeEmployerInCtc) {
        this(isEnabled, registrationNumber, null, restrictEmployeeToCeiling, includeEmployerInCtc, false, false);
    }
}
