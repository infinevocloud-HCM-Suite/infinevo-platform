package com.infinevo.payroll.proof;

import java.util.UUID;

/**
 * Service for reviewer evaluation of investment proofs (W-34.2 spec §4).
 */
public interface ProofReviewService {

    /**
     * Retrieves the proof verification review view for HR, including items, open step ids,
     * and comment counts.
     */
    ProofReviewResponse review(UUID proofId);

    /**
     * Decides an individual proof item (APPROVE, DISALLOW, or RETURN).
     */
    ProofReviewItemResponse decideItem(UUID proofId, UUID itemId, ProofItemDecisionRequest req);

    /**
     * Decides the final approval step for the proof (APPROVE or RETURN).
     */
    ProofReviewResponse decideFinal(UUID proofId, ProofFinalDecisionRequest req);
}
