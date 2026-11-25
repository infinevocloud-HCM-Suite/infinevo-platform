package com.infinevo.payroll.gratuity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to estimate an employee's gratuity as of an exit date (W-91).
 */
public record GratuityEstimateRequest(UUID employeeId, LocalDate exitDate, GratuityReason reason) {}
