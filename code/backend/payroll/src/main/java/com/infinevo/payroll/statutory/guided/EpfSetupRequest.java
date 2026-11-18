package com.infinevo.payroll.statutory.guided;

import java.time.LocalDate;

/**
 * EPF configuration inputs for guided statutory setup (W-93).
 */
public record EpfSetupRequest(
        Boolean registered,
        String registrationNumber,
        LocalDate registrationDate,
        Boolean restrictToWageCeiling,
        Boolean includeEmployerInCtc,
        Boolean includeEdliInCtc,
        Boolean includeAdminInCtc) {}
