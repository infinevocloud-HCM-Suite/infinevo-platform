package com.infinevo.payroll.statutory.guided;

import java.time.LocalDate;

/**
 * State professional tax registration inputs for guided statutory setup (W-93).
 */
public record PtStateSetupRequest(String stateCode, String registrationNumber, LocalDate effectiveFrom) {}
