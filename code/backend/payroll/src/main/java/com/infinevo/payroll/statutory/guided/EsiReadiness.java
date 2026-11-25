package com.infinevo.payroll.statutory.guided;

import java.time.LocalDate;

/**
 * Summary of ESI readiness state (W-93).
 */
public record EsiReadiness(
        boolean isEnabled, String registrationNumber, LocalDate registrationDate, boolean includeEmployerInCtc) {

    public EsiReadiness(boolean isEnabled, String registrationNumber, boolean includeEmployerInCtc) {
        this(isEnabled, registrationNumber, null, includeEmployerInCtc);
    }
}
