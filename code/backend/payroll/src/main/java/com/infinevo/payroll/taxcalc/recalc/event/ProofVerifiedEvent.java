package com.infinevo.payroll.taxcalc.recalc.event;

import java.util.UUID;

/**
 * Event published when investment proofs are verified (W-34 / W-33.3).
 */
public record ProofVerifiedEvent(UUID tenantId, UUID employeeId, UUID declarationId, String financialYear) {}
