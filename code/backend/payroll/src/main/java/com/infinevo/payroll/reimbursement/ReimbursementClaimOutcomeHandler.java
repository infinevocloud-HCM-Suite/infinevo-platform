package com.infinevo.payroll.reimbursement;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalOutcomeHandler;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outcome handler invoked by {@link com.infinevo.core.approval.OutcomeDispatcher} when a
 * REIMBURSEMENT approval flow completes (W-35.1).
 */
@Component
public class ReimbursementClaimOutcomeHandler implements ApprovalOutcomeHandler {

    private final ApprovalInstanceRepository instanceRepository;
    private final ReimbursementClaimRepository claimRepository;
    private final PayInputService payInputService;

    public ReimbursementClaimOutcomeHandler(
            ApprovalInstanceRepository instanceRepository,
            ReimbursementClaimRepository claimRepository,
            PayInputService payInputService) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository must not be null");
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
    }

    @Override
    public ApprovalFlowType flowType() {
        return ApprovalFlowType.REIMBURSEMENT;
    }

    @Override
    @Transactional
    public void onApproved(UUID instanceId, List<StepDecision> decisions) {
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
            ReimbursementClaim claim = claimRepository
                    .findByTenantIdAndId(instance.getTenantId(), instance.getSubjectId())
                    .orElseThrow(() ->
                            new NoSuchElementException("Reimbursement claim not found: " + instance.getSubjectId()));

            // Idempotent: if already approved, return
            if (claim.getStatus() == ClaimStatus.APPROVED) {
                return;
            }

            StepDecision lastDecision =
                    (decisions != null && !decisions.isEmpty()) ? decisions.get(decisions.size() - 1) : null;

            BigDecimal decisionAmount = lastDecision != null ? lastDecision.approvedAmount() : null;
            BigDecimal approvedAmount = decisionAmount != null ? decisionAmount : claim.getRequestedAmount();

            // Clamp above requested amount to requested amount
            if (approvedAmount.compareTo(claim.getRequestedAmount()) > 0) {
                approvedAmount = claim.getRequestedAmount();
            }

            // Zero becomes a rejection with remark "Approved amount was zero"
            if (approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                claim.setStatus(ClaimStatus.REJECTED);
                claim.setRemarks("Approved amount was zero");
                claimRepository.save(claim);
                return;
            }

            // Write row to the pay input ledger
            UUID claimId = claim.getId() != null ? claim.getId() : instance.getSubjectId();
            PayInputCommand command = new PayInputCommand(
                    claim.getEmployeeId(),
                    YearMonth.now(),
                    PayInputKind.REIMBURSEMENT,
                    null,
                    Money.of(approvedAmount),
                    "payroll",
                    "reimbursement_claim:" + claimId);

            PayInputResponse payInput = payInputService.record(command);

            claim.setApprovedAmount(approvedAmount);
            claim.setPayInputId(payInput.id());
            claim.setPostedPeriod(payInput.postedPeriod().toString());
            if (lastDecision != null) {
                claim.setApprovedBy(lastDecision.deciderId());
            }
            claim.setApprovedAt(Instant.now());
            claim.setStatus(ClaimStatus.APPROVED);
            claimRepository.save(claim);
        } finally {
            if (boundHere) {
                TenantContext.clear();
            }
        }
    }

    @Override
    @Transactional
    public void onRejected(UUID instanceId, List<StepDecision> decisions) {
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
            ReimbursementClaim claim = claimRepository
                    .findByTenantIdAndId(instance.getTenantId(), instance.getSubjectId())
                    .orElseThrow(() ->
                            new NoSuchElementException("Reimbursement claim not found: " + instance.getSubjectId()));

            if (claim.getStatus() == ClaimStatus.APPROVED) {
                return;
            }

            StepDecision lastDecision =
                    (decisions != null && !decisions.isEmpty()) ? decisions.get(decisions.size() - 1) : null;

            claim.setStatus(ClaimStatus.REJECTED);
            if (lastDecision != null && lastDecision.comment() != null) {
                claim.setRemarks(lastDecision.comment());
            }
            claimRepository.save(claim);
        } finally {
            if (boundHere) {
                TenantContext.clear();
            }
        }
    }
}
