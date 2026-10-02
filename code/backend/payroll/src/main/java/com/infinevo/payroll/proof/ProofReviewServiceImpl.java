package com.infinevo.payroll.proof;

import com.infinevo.core.approval.ApprovalDecideRequest;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.document.DocumentService;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ProofReviewService} (W-34.2).
 *
 * <p>Wraps approval engine decisions, enforces proof-of-investment validation rules,
 * marks item/proof statuses, and handles RETURN ordering as required by spec §3 and §9.
 */
@Service
@Transactional
public class ProofReviewServiceImpl implements ProofReviewService {

    private static final Logger log = LoggerFactory.getLogger(ProofReviewServiceImpl.class);

    private final EmployeeProofOfInvestmentRepository proofRepository;
    private final EmployeeProofItemRepository itemRepository;
    private final EmployeeProofItemDocumentRepository documentLinkRepository;
    private final EmployeeProofItemCommentRepository commentRepository;
    private final ApprovalStepRepository stepRepository;
    private final ApprovalService approvalService;
    private final TaxDeclarationWindowService windowService;
    private final DocumentService documentService;

    public ProofReviewServiceImpl(
            EmployeeProofOfInvestmentRepository proofRepository,
            EmployeeProofItemRepository itemRepository,
            EmployeeProofItemDocumentRepository documentLinkRepository,
            EmployeeProofItemCommentRepository commentRepository,
            ApprovalStepRepository stepRepository,
            ApprovalService approvalService,
            TaxDeclarationWindowService windowService,
            DocumentService documentService) {
        this.proofRepository = Objects.requireNonNull(proofRepository, "proofRepository must not be null");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository must not be null");
        this.documentLinkRepository =
                Objects.requireNonNull(documentLinkRepository, "documentLinkRepository must not be null");
        this.commentRepository = Objects.requireNonNull(commentRepository, "commentRepository must not be null");
        this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.windowService = Objects.requireNonNull(windowService, "windowService must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public ProofReviewResponse review(UUID proofId) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(proofId, "proofId must not be null");

        EmployeeProofOfInvestment proof = proofRepository
                .findByTenantIdAndId(tenantId, proofId)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment found with id " + proofId));

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, proof.getFinancialYear());
        boolean commentMandatory = window.isPoiCommentMandatory();

        UUID finalStepId = null;
        Map<String, UUID> openStepsByItemRef = Map.of();
        if (proof.getApprovalInstanceId() != null) {
            List<ApprovalStep> allSteps = stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(
                    tenantId, proof.getApprovalInstanceId());
            finalStepId = allSteps.stream()
                    .filter(s -> s.getItemRef() == null && s.getDecision() == null)
                    .map(ApprovalStep::getId)
                    .findFirst()
                    .orElse(null);
            openStepsByItemRef = allSteps.stream()
                    .filter(s -> s.getItemRef() != null && s.getDecision() == null)
                    .collect(Collectors.toMap(ApprovalStep::getItemRef, ApprovalStep::getId, (a, b) -> a));
        }

        List<EmployeeProofItem> items =
                itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proof.getId());
        Map<UUID, List<ProofDocumentResponse>> docsByItem = documentsOf(items);

        List<UUID> itemIds = items.stream().map(EmployeeProofItem::getId).toList();
        Map<UUID, Long> commentCounts = itemIds.isEmpty()
                ? Map.of()
                : commentRepository.findByTenantIdAndItemIdInOrderByCreatedAtAscIdAsc(tenantId, itemIds).stream()
                        .collect(Collectors.groupingBy(EmployeeProofItemComment::getItemId, Collectors.counting()));

        Map<String, UUID> finalOpenSteps = openStepsByItemRef;
        List<ProofReviewItemResponse> reviewItems = items.stream()
                .map(item -> {
                    UUID openStepId = finalOpenSteps.get(item.getId().toString());
                    long count = commentCounts.getOrDefault(item.getId(), 0L);
                    List<ProofDocumentResponse> docs = docsByItem.getOrDefault(item.getId(), List.of());
                    return ProofReviewItemResponse.from(item, docs, openStepId, count);
                })
                .toList();

        return new ProofReviewResponse(
                proof.getId(),
                proof.getEmployeeId(),
                proof.getFinancialYear(),
                proof.getStatus(),
                proof.getApprovalInstanceId(),
                finalStepId,
                proof.getSubmittedAt(),
                proof.getDecidedAt(),
                proof.getReviewerNote(),
                commentMandatory,
                reviewItems);
    }

    @Override
    public ProofReviewItemResponse decideItem(UUID proofId, UUID itemId, ProofItemDecisionRequest req) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(proofId, "proofId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(req, "request must not be null");

        // Load under update lock
        EmployeeProofOfInvestment proof = proofRepository
                .lockByTenantIdAndId(tenantId, proofId)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment found with id " + proofId));

        if (proof.getStatus() != ProofStatus.SUBMITTED || proof.getApprovalInstanceId() == null) {
            throw new ProofConflictException(
                    ProofConflictException.NOT_UNDER_REVIEW, "Proof is not currently under review");
        }

        EmployeeProofItem item = itemRepository
                .findByTenantIdAndProofIdAndId(tenantId, proofId, itemId)
                .orElseThrow(() -> new ProofNotFoundException("No proof item found with id " + itemId));

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, proof.getFinancialYear());
        boolean commentMandatory = window.isPoiCommentMandatory();
        ProofDecisionRules.validateItemDecision(req, item.getClaimedAmount(), commentMandatory);

        List<ApprovalStep> steps =
                stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, proof.getApprovalInstanceId());
        ApprovalStep openStep = steps.stream()
                .filter(s -> Objects.equals(s.getItemRef(), itemId.toString()) && s.getDecision() == null)
                .findFirst()
                .orElseThrow(() -> new ProofConflictException(
                        ProofConflictException.NOT_UNDER_REVIEW, "Item is not currently under review"));

        switch (req.action()) {
            case APPROVE -> {
                item.setStatus(ProofItemStatus.APPROVED);
                item.setApprovedAmount(req.approvedAmount());
                item.setReviewerNote(req.comment());
                itemRepository.save(item);
                approvalService.decide(
                        openStep.getId(),
                        new ApprovalDecideRequest(ApprovalDecision.APPROVED, req.comment(), req.approvedAmount()));
            }
            case DISALLOW -> {
                item.setStatus(ProofItemStatus.DISALLOWED);
                item.setApprovedAmount(BigDecimal.ZERO);
                item.setReviewerNote(req.comment());
                itemRepository.save(item);
                approvalService.decide(
                        openStep.getId(),
                        new ApprovalDecideRequest(ApprovalDecision.APPROVED, req.comment(), BigDecimal.ZERO));
            }
            case RETURN -> {
                // Spec §3 & §9: Mark item RETURNED and store note before calling decide(REJECTED) in same transaction
                item.setStatus(ProofItemStatus.RETURNED);
                item.setApprovedAmount(null);
                item.setReviewerNote(req.comment());
                itemRepository.save(item);
                approvalService.decide(
                        openStep.getId(), new ApprovalDecideRequest(ApprovalDecision.REJECTED, req.comment(), null));
            }
        }

        List<ProofDocumentResponse> docs = documentsOf(List.of(item)).getOrDefault(item.getId(), List.of());
        long commentCount = commentRepository.countByTenantIdAndItemId(tenantId, item.getId());
        return ProofReviewItemResponse.from(item, docs, null, commentCount);
    }

    @Override
    public ProofReviewResponse decideFinal(UUID proofId, ProofFinalDecisionRequest req) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(proofId, "proofId must not be null");
        Objects.requireNonNull(req, "request must not be null");

        EmployeeProofOfInvestment proof = proofRepository
                .lockByTenantIdAndId(tenantId, proofId)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment found with id " + proofId));

        if (proof.getStatus() != ProofStatus.SUBMITTED || proof.getApprovalInstanceId() == null) {
            throw new ProofConflictException(
                    ProofConflictException.NOT_UNDER_REVIEW, "Proof is not currently under review");
        }

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, proof.getFinancialYear());
        boolean commentMandatory = window.isPoiCommentMandatory();
        ProofDecisionRules.validateFinalDecision(req, commentMandatory);

        List<ApprovalStep> allSteps =
                stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, proof.getApprovalInstanceId());

        boolean hasUndecidedItems = allSteps.stream().anyMatch(s -> s.getItemRef() != null && s.getDecision() == null);
        if (hasUndecidedItems) {
            throw new ProofConflictException(
                    ProofConflictException.ITEMS_UNDECIDED,
                    "Cannot make final decision until all claimed items are decided");
        }

        ApprovalStep finalStep = allSteps.stream()
                .filter(s -> s.getItemRef() == null && s.getDecision() == null)
                .findFirst()
                .orElseThrow(() -> new ProofConflictException(
                        ProofConflictException.NOT_UNDER_REVIEW, "No open final approval step found for proof"));

        switch (req.action()) {
            case APPROVE -> {
                approvalService.decide(
                        finalStep.getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, req.comment(), null));
            }
            case RETURN -> {
                // Spec §3: RETURN -> proof.reviewer_note = comment, then decide REJECTED
                proof.setReviewerNote(req.comment());
                proofRepository.save(proof);
                approvalService.decide(
                        finalStep.getId(), new ApprovalDecideRequest(ApprovalDecision.REJECTED, req.comment(), null));
            }
        }

        return review(proofId);
    }

    private Map<UUID, List<ProofDocumentResponse>> documentsOf(List<EmployeeProofItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        UUID tenantId = items.get(0).getTenantId();
        List<UUID> itemIds = items.stream().map(EmployeeProofItem::getId).toList();
        Map<UUID, List<ProofDocumentResponse>> byItem = new HashMap<>();
        for (EmployeeProofItemDocument link :
                documentLinkRepository.findByTenantIdAndItemIdInOrderByCreatedAtAscIdAsc(tenantId, itemIds)) {
            try {
                var doc = documentService.get(link.getDocumentId());
                byItem.computeIfAbsent(link.getItemId(), k -> new ArrayList<>())
                        .add(new ProofDocumentResponse(
                                doc.id(), doc.fileName(), doc.contentType(), doc.sizeBytes(), doc.createdAt()));
            } catch (DocumentService.NotFoundException e) {
                log.debug("Proof item {} links a document that no longer exists", link.getItemId());
            }
        }
        return byItem;
    }
}
