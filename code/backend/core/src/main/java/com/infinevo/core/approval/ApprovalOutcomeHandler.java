package com.infinevo.core.approval;

import java.util.List;
import java.util.UUID;

/**
 * Outcome contract implemented by consumers for approval completion side effects (W-15.1, spec section 4).
 * Spring beans implementing this are keyed by {@link #flowType()}.
 */
public interface ApprovalOutcomeHandler {

    ApprovalFlowType flowType();

    void onApproved(UUID instanceId, List<StepDecision> decisions);

    void onRejected(UUID instanceId, List<StepDecision> decisions);
}
