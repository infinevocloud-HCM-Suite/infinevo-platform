package com.infinevo.payroll.proof;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing proof item comments for both reviewers and employees (W-34.2 spec §4).
 */
public interface ProofCommentService {

    /**
     * Lists comments on a proof item for an HR reviewer, validating that the item belongs to proofId.
     */
    List<ProofCommentResponse> listForReviewer(UUID proofId, UUID itemId);

    /**
     * Adds a comment on a proof item by an HR reviewer.
     * The authorRole is strictly set to REVIEWER server-side.
     */
    ProofCommentResponse addForReviewer(UUID proofId, UUID itemId, ProofCommentRequest request);

    /**
     * Lists comments on a proof item for the current employee, validating ownership of the proof and item.
     */
    List<ProofCommentResponse> listForOwn(String financialYear, UUID itemId);

    /**
     * Adds a comment on a proof item by the current employee.
     * The authorRole is strictly set to EMPLOYEE server-side.
     */
    ProofCommentResponse addForOwn(String financialYear, UUID itemId, ProofCommentRequest request);
}
