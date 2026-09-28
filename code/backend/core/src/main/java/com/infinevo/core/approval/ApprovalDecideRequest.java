package com.infinevo.core.approval;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Request body for deciding an approval step (W-15.2, spec section 4).
 */
public record ApprovalDecideRequest(ApprovalDecision decision, String comment, BigDecimal approvedAmount) {

    public ApprovalDecideRequest {
        Objects.requireNonNull(decision, "decision must not be null");
        if (approvedAmount != null && approvedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("approvedAmount must not be negative");
        }
    }

    public ApprovalDecideRequest(ApprovalDecision decision, String comment) {
        this(decision, comment, null);
    }
}
