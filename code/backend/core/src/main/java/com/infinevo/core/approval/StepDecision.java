package com.infinevo.core.approval;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record of an individual step decision passed to {@link ApprovalOutcomeHandler} upon completion (W-15.1, W-15.2).
 */
public record StepDecision(
        UUID stepId, UUID deciderId, String decision, String comment, BigDecimal approvedAmount, String itemRef) {

    public StepDecision(UUID stepId, UUID deciderId, String decision, String comment) {
        this(stepId, deciderId, decision, comment, null, null);
    }
}
