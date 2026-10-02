package com.infinevo.payroll.proof;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts an approval instance when an employee submits a proof of investment (W-34.2 spec §3 and §4).
 *
 * <p>Listens synchronously on {@link ProofSubmittedEvent} in the submitting transaction, starts an approval
 * flow of type {@link ApprovalFlowType#PROOF_OF_INVESTMENT} with per-item steps for every claimed item
 * (claimed amount &gt; 0), and stores the resulting {@code approvalInstanceId} on the proof record.
 */
@Component
public class ProofApprovalStarter {

    private static final Logger log = LoggerFactory.getLogger(ProofApprovalStarter.class);
    private static final String SUBJECT_TABLE = "payroll.employee_proof_of_investment";

    private final ApprovalService approvalService;
    private final EmployeeProofOfInvestmentRepository proofRepository;
    private final EmployeeProofItemRepository itemRepository;

    public ProofApprovalStarter(
            ApprovalService approvalService,
            EmployeeProofOfInvestmentRepository proofRepository,
            EmployeeProofItemRepository itemRepository) {
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.proofRepository = Objects.requireNonNull(proofRepository, "proofRepository must not be null");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository must not be null");
    }

    @EventListener
    @Transactional
    public void onProofSubmitted(ProofSubmittedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        UUID tenantId = event.tenantId();
        UUID proofId = event.proofId();
        UUID employeeId = event.employeeId();

        boolean clearTenant = false;
        if (TenantContext.current().isEmpty()) {
            TenantContext.set(tenantId);
            clearTenant = true;
        }

        try {
            EmployeeProofOfInvestment proof = proofRepository
                    .findByTenantIdAndId(tenantId, proofId)
                    .orElseThrow(() -> new IllegalStateException("Proof of investment not found: " + proofId));

            List<EmployeeProofItem> items =
                    itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId);

            List<String> claimedItemRefs = items.stream()
                    .filter(i ->
                            i.getClaimedAmount() != null && i.getClaimedAmount().compareTo(BigDecimal.ZERO) > 0)
                    .map(i -> i.getId().toString())
                    .toList();

            SubjectRef subject = new SubjectRef(SUBJECT_TABLE, proofId);
            UUID instanceId =
                    approvalService.start(ApprovalFlowType.PROOF_OF_INVESTMENT, subject, employeeId, claimedItemRefs);

            proof.setApprovalInstanceId(instanceId);
            proofRepository.save(proof);

            log.info(
                    "Started approval instance {} for proof {} (employee {}, {} claimed items)",
                    instanceId,
                    proofId,
                    employeeId,
                    claimedItemRefs.size());
        } finally {
            if (clearTenant) {
                TenantContext.clear();
            }
        }
    }
}
