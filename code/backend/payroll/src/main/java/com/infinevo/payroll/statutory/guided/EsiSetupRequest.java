package com.infinevo.payroll.statutory.guided;

import java.time.LocalDate;

/**
 * ESI configuration inputs for guided statutory setup (W-93).
 */
public record EsiSetupRequest(
        Boolean registered, String registrationNumber, LocalDate registrationDate, Boolean includeEmployerInCtc) {}
