package com.infinevo.payroll.taxcalc.recalc.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Event published when an employee's salary structure is created, revised, or cancelled (W-26.2 / W-33.3).
 */
public record SalaryVersionChangedEvent(UUID tenantId, UUID employeeId, LocalDate effectiveFrom) {}
