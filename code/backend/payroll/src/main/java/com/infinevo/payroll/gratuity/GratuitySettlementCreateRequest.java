package com.infinevo.payroll.gratuity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to record a formal gratuity settlement (W-91).
 */
public record GratuitySettlementCreateRequest(UUID employeeId, LocalDate exitDate, GratuityReason reason) {}
