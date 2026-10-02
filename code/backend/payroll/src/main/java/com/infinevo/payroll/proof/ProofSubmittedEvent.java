package com.infinevo.payroll.proof;

import java.util.UUID;

/**
 * Published, in the submitting transaction, when an employee submits a proof of investment (W-34.1).
 * W-34.2 listens and starts the review.
 */
public record ProofSubmittedEvent(UUID tenantId, UUID proofId, UUID employeeId, String financialYear) {}
