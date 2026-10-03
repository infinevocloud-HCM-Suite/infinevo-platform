package com.infinevo.hrms.overtime;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalOutcomeHandler;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.core.overtime.OvertimeResponse;
import com.infinevo.core.overtime.OvertimeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the outcome of an {@code OVERTIME} approval (W-40.6 §4, "The handler"): approved calls
 * {@link OvertimeService#approve}, which posts the one {@code OVERTIME} pay input row; rejected calls
 * {@link OvertimeService#reject}. Both return quietly when called a second time (W-40.5 §4), so the handler holds no
 * state of its own.
 *
 * <p>The dispatcher may call with no tenant bound (the sweep); the tenant is bound from the instance and cleared after,
 * as {@code payroll/.../reimbursement/ReimbursementClaimOutcomeHandler.java:44-62,116-120}. If {@code approve} throws,
 * the dispatcher's transaction rolls back and the sweep retries ({@code OutcomeDispatcher.java:86-93}).
 */
@Component
public class OvertimeRequestOutcomeHandler implements ApprovalOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(OvertimeRequestOutcomeHandler.class);

    private final ApprovalInstanceRepository instanceRepository;
    private final OvertimeService overtimeService;

    public OvertimeRequestOutcomeHandler(
            ApprovalInstanceRepository instanceRepository, OvertimeService overtimeService) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.overtimeService = Objects.requireNonNull(overtimeService, "overtimeService must not be null");
    }

    @Override
    public ApprovalFlowType flowType() {
        return ApprovalFlowType.OVERTIME;
    }

    @Override
    @Transactional
    public void onApproved(UUID instanceId, List<StepDecision> decisions) {
        apply(instanceId, true);
    }

    @Override
    @Transactional
    public void onRejected(UUID instanceId, List<StepDecision> decisions) {
        apply(instanceId, false);
    }

    private void apply(UUID instanceId, boolean approved) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        ApprovalInstance instance = instanceRepository
                .findById(instanceId)
                .orElseThrow(() -> new NoSuchElementException("Approval instance not found: " + instanceId));

        boolean boundHere = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(instance.getTenantId());
            boundHere = true;
        }
        try {
            UUID requestId = instance.getSubjectId();
            OvertimeResponse result = approved ? overtimeService.approve(requestId) : overtimeService.reject(requestId);
            log.info(
                    "Overtime request {} is {} after instance {} in tenant {}",
                    requestId,
                    result != null ? result.status() : (approved ? "APPROVED" : "REJECTED"),
                    instanceId,
                    instance.getTenantId());
        } finally {
            if (boundHere) {
                TenantContext.clear();
            }
        }
    }
}
