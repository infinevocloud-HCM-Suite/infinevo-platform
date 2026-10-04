package com.infinevo.payroll.proof;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalOutcomeHandler;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.payroll.taxcalc.recalc.event.ProofVerifiedEvent;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outcome handler invoked by {@link com.infinevo.core.approval.OutcomeDispatcher} when a
 * {@link ApprovalFlowType#PROOF_OF_INVESTMENT} approval flow completes (W-34.2).
 *
 * <p>Idempotency guard: only acts when the proof status is {@link ProofStatus#SUBMITTED}
 * and its approval instance id matches the triggering instance.
 */
@Component
public class ProofOutcomeHandler implements ApprovalOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(ProofOutcomeHandler.class);

    private final ApprovalInstanceRepository instanceRepository;
    private final EmployeeProofOfInvestmentRepository proofRepository;
    private final EmployeeProofItemRepository itemRepository;
    private final EmployeeService employeeService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** For tests: a fixed clock. Spring uses the other constructor; there is no Clock bean. */
    ProofOutcomeHandler(
            ApprovalInstanceRepository instanceRepository,
            EmployeeProofOfInvestmentRepository proofRepository,
            EmployeeProofItemRepository itemRepository,
            EmployeeService employeeService,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.proofRepository = Objects.requireNonNull(proofRepository, "proofRepository must not be null");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Autowired
    public ProofOutcomeHandler(
            ApprovalInstanceRepository instanceRepository,
            EmployeeProofOfInvestmentRepository proofRepository,
            EmployeeProofItemRepository itemRepository,
            EmployeeService employeeService,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher) {
        this(
                instanceRepository,
                proofRepository,
                itemRepository,
                employeeService,
                notificationService,
                eventPublisher,
                Clock.systemUTC());
    }

    @Override
    public ApprovalFlowType flowType() {
        return ApprovalFlowType.PROOF_OF_INVESTMENT;
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
            UUID tenantId = instance.getTenantId();
            UUID proofId = instance.getSubjectId();

            Optional<EmployeeProofOfInvestment> proofOpt = proofRepository.findByTenantIdAndId(tenantId, proofId);
            if (proofOpt.isEmpty()) {
                log.warn("Ignoring outcome: proof {} not found in tenant {}", proofId, tenantId);
                return;
            }

            EmployeeProofOfInvestment proof = proofOpt.get();
            if (proof.getStatus() != ProofStatus.SUBMITTED
                    || !Objects.equals(proof.getApprovalInstanceId(), instanceId)) {
                log.warn(
                        "Ignoring outcome for proof {}: status={}, instance={}",
                        proof.getId(),
                        proof.getStatus(),
                        proof.getApprovalInstanceId());
                return;
            }

            List<EmployeeProofItem> items =
                    itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proof.getId());

            Map<String, StepDecision> itemDecisions = (decisions != null)
                    ? decisions.stream()
                            .filter(d -> d.itemRef() != null)
                            .collect(Collectors.toMap(StepDecision::itemRef, Function.identity(), (a, b) -> b))
                    : Map.of();

            for (EmployeeProofItem item : items) {
                StepDecision itemDecision = itemDecisions.get(item.getId().toString());
                if (itemDecision != null) {
                    BigDecimal approvedAmount = itemDecision.approvedAmount() != null
                            ? itemDecision.approvedAmount()
                            : (item.getApprovedAmount() != null ? item.getApprovedAmount() : item.getClaimedAmount());
                    if (item.getClaimedAmount() != null && approvedAmount.compareTo(item.getClaimedAmount()) > 0) {
                        log.warn(
                                "Approved amount {} for item {} exceeded claimed amount {}; clamping to claimed",
                                approvedAmount,
                                item.getId(),
                                item.getClaimedAmount());
                        approvedAmount = item.getClaimedAmount();
                    }
                    item.setApprovedAmount(approvedAmount);
                    if (approvedAmount.compareTo(BigDecimal.ZERO) == 0) {
                        item.setStatus(ProofItemStatus.DISALLOWED);
                    } else {
                        item.setStatus(ProofItemStatus.APPROVED);
                    }
                    if (itemDecision.comment() != null
                            && !itemDecision.comment().isBlank()) {
                        item.setReviewerNote(itemDecision.comment());
                    }
                } else {
                    // No step decided this item. Every claimed item gets a step when the review starts
                    // (ProofApprovalStarter), so this is an unclaimed item, or one the engine did not
                    // see. Either way nobody reviewed an amount: nothing is allowed, and an amount is
                    // never approved by default. The approved figure is what reduces the employee's tax.
                    if (ProofRules.isClaimed(item)) {
                        log.warn(
                                "Proof item {} was claimed but no approval step decided it; allowing nothing",
                                item.getId());
                    }
                    item.setApprovedAmount(BigDecimal.ZERO);
                    item.setStatus(ProofItemStatus.DISALLOWED);
                }
                item.setUpdatedBy("system");
            }
            itemRepository.saveAll(items);

            proof.setStatus(ProofStatus.APPROVED);
            proof.setDecidedAt(clock.instant());
            proof.setUpdatedBy("system");
            proofRepository.save(proof);

            // Published inside this transaction, as ProofSubmittedEvent is: the tax recalculation listens
            // after commit, so it only ever sees an approval that was written.
            eventPublisher.publishEvent(new ProofVerifiedEvent(
                    tenantId, proof.getEmployeeId(), proof.getDeclarationId(), proof.getFinancialYear()));

            notifyDecided(proof, "Approved");
            log.info("Proof of investment {} marked APPROVED for instance {}", proof.getId(), instanceId);
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
            UUID tenantId = instance.getTenantId();
            UUID proofId = instance.getSubjectId();

            Optional<EmployeeProofOfInvestment> proofOpt = proofRepository.findByTenantIdAndId(tenantId, proofId);
            if (proofOpt.isEmpty()) {
                log.warn("Ignoring outcome: proof {} not found in tenant {}", proofId, tenantId);
                return;
            }

            EmployeeProofOfInvestment proof = proofOpt.get();
            if (proof.getStatus() != ProofStatus.SUBMITTED
                    || !Objects.equals(proof.getApprovalInstanceId(), instanceId)) {
                log.warn(
                        "Ignoring outcome for proof {}: status={}, instance={}",
                        proof.getId(),
                        proof.getStatus(),
                        proof.getApprovalInstanceId());
                return;
            }

            List<EmployeeProofItem> items =
                    itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proof.getId());

            boolean hasReturnedItem = false;
            for (EmployeeProofItem item : items) {
                if (item.getStatus() == ProofItemStatus.RETURNED) {
                    hasReturnedItem = true;
                } else {
                    item.setStatus(ProofItemStatus.PENDING);
                    item.setApprovedAmount(null);
                    item.setUpdatedBy("system");
                }
            }
            itemRepository.saveAll(items);

            if (!hasReturnedItem
                    && (proof.getReviewerNote() == null
                            || proof.getReviewerNote().isBlank())) {
                StepDecision rejectedDecision = (decisions != null)
                        ? decisions.stream()
                                .filter(d -> "REJECTED".equalsIgnoreCase(d.decision()))
                                .findFirst()
                                .orElse(null)
                        : null;
                if (rejectedDecision != null
                        && rejectedDecision.comment() != null
                        && !rejectedDecision.comment().isBlank()) {
                    proof.setReviewerNote(rejectedDecision.comment());
                }
            }

            proof.setStatus(ProofStatus.REJECTED);
            proof.setDecidedAt(clock.instant());
            proof.setUpdatedBy("system");
            proofRepository.save(proof);

            notifyDecided(proof, "Returned");
            log.info("Proof of investment {} marked REJECTED (Returned) for instance {}", proof.getId(), instanceId);
        } finally {
            if (boundHere) {
                TenantContext.clear();
            }
        }
    }

    private void notifyDecided(EmployeeProofOfInvestment proof, String decision) {
        String employeeName = "Employee";
        try {
            EmployeeResponse emp = employeeService.get(proof.getEmployeeId());
            if (emp != null) {
                String first = emp.firstName() != null ? emp.firstName() : "";
                String last = emp.lastName() != null ? emp.lastName() : "";
                String full = (first + " " + last).trim();
                if (!full.isBlank()) {
                    employeeName = full;
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch employee {} for notification: {}", proof.getEmployeeId(), e.getMessage());
        }

        try {
            notificationService.compose(
                    NotificationEvent.APPROVAL_DECIDED,
                    proof.getEmployeeId(),
                    Map.of(
                            "employee_name", employeeName,
                            "request_title", "Proof of Investment " + proof.getFinancialYear(),
                            "decision", decision));
        } catch (RuntimeException e) {
            log.warn("APPROVAL_DECIDED not composed for proof {} in tenant {}", proof.getId(), proof.getTenantId(), e);
        }
    }
}
