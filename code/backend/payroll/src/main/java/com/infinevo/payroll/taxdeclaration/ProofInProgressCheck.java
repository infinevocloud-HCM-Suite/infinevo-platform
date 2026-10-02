package com.infinevo.payroll.taxdeclaration;

import java.util.UUID;

/**
 * Asks whether a declaration is under proof-of-investment review (W-34.1).
 *
 * <p>The declaration side owns the question and the proof side answers it, so the declaration
 * package needs no import from {@code proof}. While a proof is {@code SUBMITTED} or {@code APPROVED}
 * its items mirror the declared lines, and the employee reopening the declaration would change the
 * lines under a reviewer (spec section 13, decision 5).
 */
public interface ProofInProgressCheck {

    /** A check that never blocks: for code that builds the service without the proof feature. */
    ProofInProgressCheck NONE = (tenantId, declarationId) -> false;

    /** {@code true} when the declaration's proof is {@code SUBMITTED} or {@code APPROVED}. */
    boolean isProofInProgress(UUID tenantId, UUID declarationId);
}
