package com.infinevo.payroll.taxcalc.recalc.event;

import java.util.UUID;

/**
 * Event published after an investment declaration is submitted (W-32.1 / W-33.3).
 */
public record DeclarationSubmittedEvent(UUID tenantId, UUID employeeId, UUID declarationId, String financialYear) {}
