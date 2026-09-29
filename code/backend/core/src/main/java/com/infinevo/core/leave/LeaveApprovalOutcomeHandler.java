package com.infinevo.core.leave;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalOutcomeHandler;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles approval engine outcomes for leave requests (W-16.3, spec section 3 &amp; 4).
 * Transitions PENDING requests to APPROVED or REJECTED when the approval engine completes.
 */
@Component
public class LeaveApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(LeaveApprovalOutcomeHandler.class);

    private final ApprovalInstanceRepository instanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveApprovalOutcomeHandler(
            ApprovalInstanceRepository instanceRepository, LeaveRequestRepository leaveRequestRepository) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.leaveRequestRepository =
                Objects.requireNonNull(leaveRequestRepository, "leaveRequestRepository must not be null");
    }

    @Override
    public ApprovalFlowType flowType() {
        return ApprovalFlowType.LEAVE;
    }

    @Override
    @Transactional
    public void onApproved(UUID instanceId, List<StepDecision> decisions) {
        ApprovalInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            log.warn("Approval instance {} not found during leave approval outcome", instanceId);
            return;
        }
        UUID requestId = instance.getSubjectId();
        UUID tenantId = instance.getTenantId();
        boolean clearTenant = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(tenantId);
            clearTenant = true;
        }
        try {
            leaveRequestRepository.findByIdAndTenantId(requestId, tenantId).ifPresent(req -> {
                if (req.getStatus() == LeaveRequestStatus.PENDING) {
                    req.setStatus(LeaveRequestStatus.APPROVED);
                    req.setDecidedAt(Instant.now());
                    leaveRequestRepository.save(req);
                    log.info("Leave request {} approved via approval instance {}", requestId, instanceId);
                }
            });
        } finally {
            if (clearTenant) {
                TenantContext.clear();
            }
        }
    }

    @Override
    @Transactional
    public void onRejected(UUID instanceId, List<StepDecision> decisions) {
        ApprovalInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            log.warn("Approval instance {} not found during leave rejection outcome", instanceId);
            return;
        }
        UUID requestId = instance.getSubjectId();
        UUID tenantId = instance.getTenantId();
        boolean clearTenant = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(tenantId);
            clearTenant = true;
        }
        try {
            leaveRequestRepository.findByIdAndTenantId(requestId, tenantId).ifPresent(req -> {
                if (req.getStatus() == LeaveRequestStatus.PENDING) {
                    req.setStatus(LeaveRequestStatus.REJECTED);
                    req.setDecidedAt(Instant.now());
                    leaveRequestRepository.save(req);
                    log.info("Leave request {} rejected via approval instance {}", requestId, instanceId);
                }
            });
        } finally {
            if (clearTenant) {
                TenantContext.clear();
            }
        }
    }
}
