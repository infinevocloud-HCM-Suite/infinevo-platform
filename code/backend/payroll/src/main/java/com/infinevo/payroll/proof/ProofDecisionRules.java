package com.infinevo.payroll.proof;

import java.math.BigDecimal;

/**
 * Pure validation rules for proof-of-investment review decisions and comments (W-34.2 spec §3 and §4).
 */
public final class ProofDecisionRules {

    public static final int MAX_COMMENT_LENGTH = 1000;

    private ProofDecisionRules() {}

    /**
     * Validates an item-level review decision.
     *
     * @param req decision request payload
     * @param claimedAmount claimed amount on the item
     * @param poiCommentMandatory whether comment is mandatory for all review actions
     * @throws ProofValidationException if any business validation fails
     */
    public static void validateItemDecision(
            ProofItemDecisionRequest req, BigDecimal claimedAmount, boolean poiCommentMandatory) {
        if (req == null) {
            throw new ProofValidationException("Decision request must not be null");
        }
        if (req.action() == null) {
            throw new ProofValidationException("Action must not be null");
        }

        if (req.comment() != null && req.comment().length() > MAX_COMMENT_LENGTH) {
            throw new ProofValidationException("Comment must not exceed " + MAX_COMMENT_LENGTH + " characters");
        }

        BigDecimal effectiveClaimed = claimedAmount != null ? claimedAmount : BigDecimal.ZERO;

        switch (req.action()) {
            case APPROVE -> {
                if (req.approvedAmount() == null) {
                    throw new ProofValidationException("Approved amount is required for APPROVE");
                }
                if (req.approvedAmount().compareTo(BigDecimal.ZERO) < 0) {
                    throw new ProofValidationException("Approved amount cannot be negative");
                }
                if (req.approvedAmount().compareTo(effectiveClaimed) > 0) {
                    throw new ProofValidationException(
                            "Approved amount cannot exceed claimed amount: " + effectiveClaimed);
                }
                // Partial approval requires comment
                if (req.approvedAmount().compareTo(effectiveClaimed) < 0 && isBlank(req.comment())) {
                    throw new ProofValidationException("Comment is required for partial approval");
                }
                // Global mandatory flag
                if (poiCommentMandatory && isBlank(req.comment())) {
                    throw new ProofValidationException("Comment is mandatory");
                }
            }
            case DISALLOW -> {
                if (req.approvedAmount() != null && req.approvedAmount().compareTo(BigDecimal.ZERO) != 0) {
                    throw new ProofValidationException("Approved amount must be 0 for DISALLOW");
                }
                if (isBlank(req.comment())) {
                    throw new ProofValidationException("Comment is required when disallowing an item");
                }
            }
            case RETURN -> {
                if (isBlank(req.comment())) {
                    throw new ProofValidationException("Comment is required when returning an item");
                }
            }
        }
    }

    /**
     * Validates the final proof-level decision.
     *
     * @param req final decision request payload
     * @param poiCommentMandatory whether comment is mandatory for all review actions
     * @throws ProofValidationException if validation fails
     */
    public static void validateFinalDecision(ProofFinalDecisionRequest req, boolean poiCommentMandatory) {
        if (req == null) {
            throw new ProofValidationException("Final decision request must not be null");
        }
        if (req.action() == null) {
            throw new ProofValidationException("Action must not be null");
        }

        if (req.comment() != null && req.comment().length() > MAX_COMMENT_LENGTH) {
            throw new ProofValidationException("Comment must not exceed " + MAX_COMMENT_LENGTH + " characters");
        }

        switch (req.action()) {
            case APPROVE -> {
                if (poiCommentMandatory && isBlank(req.comment())) {
                    throw new ProofValidationException("Comment is mandatory");
                }
            }
            case RETURN -> {
                if (isBlank(req.comment())) {
                    throw new ProofValidationException("Comment is required when returning proof");
                }
            }
        }
    }

    /**
     * Validates a comment creation request body.
     *
     * @param req comment request payload
     * @throws ProofValidationException if body is null, blank, or exceeds max characters
     */
    public static void validateComment(ProofCommentRequest req) {
        if (req == null || isBlank(req.body())) {
            throw new ProofValidationException("Comment body must not be blank");
        }
        if (req.body().length() > MAX_COMMENT_LENGTH) {
            throw new ProofValidationException("Comment body must not exceed " + MAX_COMMENT_LENGTH + " characters");
        }
    }

    private static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}
