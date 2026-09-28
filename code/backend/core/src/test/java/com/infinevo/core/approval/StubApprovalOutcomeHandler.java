package com.infinevo.core.approval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Test double implementing {@link ApprovalOutcomeHandler} for {@link ApprovalFlowType#REGULARIZATION} (W-15.2, spec section 4 & 7).
 */
@Component
public class StubApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final List<UUID> approvedInstances = new CopyOnWriteArrayList<>();
    private final List<UUID> rejectedInstances = new CopyOnWriteArrayList<>();
    private final Map<UUID, List<StepDecision>> approvedDecisions = new ConcurrentHashMap<>();
    private final Map<UUID, List<StepDecision>> rejectedDecisions = new ConcurrentHashMap<>();
    private Consumer<UUID> onApprovedHook;
    private Runnable beforeApprovedHook;

    @Override
    public ApprovalFlowType flowType() {
        return ApprovalFlowType.REGULARIZATION;
    }

    @Override
    public void onApproved(UUID instanceId, List<StepDecision> decisions) {
        if (beforeApprovedHook != null) {
            beforeApprovedHook.run();
        }
        approvedInstances.add(instanceId);
        approvedDecisions.put(instanceId, new ArrayList<>(decisions));
        if (onApprovedHook != null) {
            onApprovedHook.accept(instanceId);
        }
    }

    @Override
    public void onRejected(UUID instanceId, List<StepDecision> decisions) {
        rejectedInstances.add(instanceId);
        rejectedDecisions.put(instanceId, new ArrayList<>(decisions));
    }

    public List<UUID> getApprovedInstances() {
        return Collections.unmodifiableList(approvedInstances);
    }

    public List<UUID> getRejectedInstances() {
        return Collections.unmodifiableList(rejectedInstances);
    }

    public List<StepDecision> getApprovedDecisions(UUID instanceId) {
        return approvedDecisions.getOrDefault(instanceId, Collections.emptyList());
    }

    public List<StepDecision> getRejectedDecisions(UUID instanceId) {
        return rejectedDecisions.getOrDefault(instanceId, Collections.emptyList());
    }

    public void setOnApprovedHook(Consumer<UUID> hook) {
        this.onApprovedHook = hook;
    }

    public void setBeforeApprovedHook(Runnable hook) {
        this.beforeApprovedHook = hook;
    }

    public void reset() {
        approvedInstances.clear();
        rejectedInstances.clear();
        approvedDecisions.clear();
        rejectedDecisions.clear();
        onApprovedHook = null;
        beforeApprovedHook = null;
    }
}
