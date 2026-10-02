package com.infinevo.payroll.proof;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.payroll.proof.ProofItemSync.Plan;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclarationRepository;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation of {@link ProofService} (W-34.1).
 *
 * <p>Concurrency: creating the proof, syncing its items and every change to it take a row lock on the
 * proof (or, for the very first read, on the declaration), so two requests on one proof queue up.
 * Submit is the one that matters: a double click must publish one {@link ProofSubmittedEvent}, and the
 * second request finds {@code SUBMITTED} and gets {@code ALREADY_SUBMITTED}.
 */
@Service
@Transactional
public class ProofServiceImpl implements ProofService {

    private static final Logger log = LoggerFactory.getLogger(ProofServiceImpl.class);

    private final EmployeeProofOfInvestmentRepository proofRepository;
    private final EmployeeProofItemRepository itemRepository;
    private final EmployeeProofItemDocumentRepository documentLinkRepository;
    private final EmployeeInvestmentDeclarationRepository declarationRepository;
    private final ProofDeclarationLockRepository declarationLockRepository;
    private final TaxDeclarationWindowService windowService;
    private final ProofSourceReader sourceReader;
    private final DocumentService documentService;
    private final EmployeeService employeeService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate notificationTransaction;
    private final Clock clock;

    @Autowired
    public ProofServiceImpl(
            EmployeeProofOfInvestmentRepository proofRepository,
            EmployeeProofItemRepository itemRepository,
            EmployeeProofItemDocumentRepository documentLinkRepository,
            EmployeeInvestmentDeclarationRepository declarationRepository,
            ProofDeclarationLockRepository declarationLockRepository,
            TaxDeclarationWindowService windowService,
            ProofSourceReader sourceReader,
            DocumentService documentService,
            EmployeeService employeeService,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this(
                proofRepository,
                itemRepository,
                documentLinkRepository,
                declarationRepository,
                declarationLockRepository,
                windowService,
                sourceReader,
                documentService,
                employeeService,
                notificationService,
                eventPublisher,
                transactionManager,
                TaxDeclarationRules.defaultClock());
    }

    ProofServiceImpl(
            EmployeeProofOfInvestmentRepository proofRepository,
            EmployeeProofItemRepository itemRepository,
            EmployeeProofItemDocumentRepository documentLinkRepository,
            EmployeeInvestmentDeclarationRepository declarationRepository,
            ProofDeclarationLockRepository declarationLockRepository,
            TaxDeclarationWindowService windowService,
            ProofSourceReader sourceReader,
            DocumentService documentService,
            EmployeeService employeeService,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.proofRepository = Objects.requireNonNull(proofRepository, "proofRepository must not be null");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository must not be null");
        this.documentLinkRepository =
                Objects.requireNonNull(documentLinkRepository, "documentLinkRepository must not be null");
        this.declarationRepository =
                Objects.requireNonNull(declarationRepository, "declarationRepository must not be null");
        this.declarationLockRepository =
                Objects.requireNonNull(declarationLockRepository, "declarationLockRepository must not be null");
        this.windowService = Objects.requireNonNull(windowService, "windowService must not be null");
        this.sourceReader = Objects.requireNonNull(sourceReader, "sourceReader must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        // The notification is its own transaction: a failure inside the notification service, a database
        // error included, must not poison the one that recorded the submit.
        this.notificationTransaction = new TransactionTemplate(transactionManager);
        this.notificationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    // ── the caller's own proof ───────────────────────────────────────────────────────────────

    @Override
    public ProofResponse readOwn(String financialYear) {
        EmployeeResponse me = currentEmployeeOrDeny("payroll.proof.read_own");
        EmployeeProofOfInvestment proof = ensureProof(me.id(), financialYear, actorOf(me));
        return toResponse(proof);
    }

    @Override
    public ProofItemResponse updateItemOwn(String financialYear, UUID itemId, ProofItemUpdateRequest request) {
        EmployeeResponse me = currentEmployeeOrDeny("payroll.proof.submit_own");
        if (request == null) {
            throw new ProofValidationException("The request body is required");
        }
        EmployeeProofOfInvestment proof = lockOwnProof(me.id(), financialYear);
        ProofRules.requireEditable(isProofOpen(proof), proof.getStatus());
        EmployeeProofItem item = requireItem(proof, itemId);

        ProofRules.validateClaimedAmount(request.claimedAmount());
        ProofRules.validateNote(request.employeeNote());
        item.setClaimedAmount(request.claimedAmount());
        item.setEmployeeNote(request.employeeNote());
        item.setUpdatedBy(actorOf(me));
        item = itemRepository.save(item);
        return ProofItemResponse.from(item, documentsOf(List.of(item)).getOrDefault(item.getId(), List.of()));
    }

    @Override
    public ProofDocumentResponse attachOwn(String financialYear, UUID itemId, String fileName, InputStream content) {
        EmployeeResponse me = currentEmployeeOrDeny("payroll.proof.submit_own");
        UUID tenantId = TenantContext.require();
        EmployeeProofOfInvestment proof = lockOwnProof(me.id(), financialYear);
        ProofRules.requireEditable(isProofOpen(proof), proof.getStatus());
        EmployeeProofItem item = requireItem(proof, itemId);

        if (liveLinksOf(tenantId, List.of(item.getId()))
                        .getOrDefault(item.getId(), List.of())
                        .size()
                >= ProofRules.MAX_DOCUMENTS_PER_ITEM) {
            throw new ProofConflictException(
                    ProofConflictException.DOCUMENT_LIMIT,
                    "An item holds at most " + ProofRules.MAX_DOCUMENTS_PER_ITEM + " files");
        }
        // The item is proven to be this caller's before a byte is stored. The document row and the link
        // share this transaction, so a failure below leaves neither behind.
        UUID documentId = documentService.store(DocumentKind.INVESTMENT_PROOF, me.id(), fileName, content);
        documentLinkRepository.saveAndFlush(
                new EmployeeProofItemDocument(tenantId, item.getId(), documentId, actorOf(me)));
        return toDocumentResponse(documentService.get(documentId));
    }

    @Override
    public void detachOwn(String financialYear, UUID itemId, UUID documentId) {
        EmployeeResponse me = currentEmployeeOrDeny("payroll.proof.submit_own");
        UUID tenantId = TenantContext.require();
        EmployeeProofOfInvestment proof = lockOwnProof(me.id(), financialYear);
        ProofRules.requireEditable(isProofOpen(proof), proof.getStatus());
        EmployeeProofItem item = requireItem(proof, itemId);

        EmployeeProofItemDocument link = documentLinkRepository
                .findByTenantIdAndItemIdAndDocumentId(tenantId, item.getId(), documentId)
                .orElseThrow(() -> new ProofNotFoundException("No such file on this item"));
        documentLinkRepository.delete(link);
        softDelete(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentService.DocumentContent openDocumentOwn(String financialYear, UUID itemId, UUID documentId) {
        EmployeeResponse me = currentEmployeeOrDeny("payroll.proof.read_own");
        return openLinked(findProof(me.id(), financialYear), itemId, documentId);
    }

    @Override
    public ProofResponse submitOwn(String financialYear) {
        EmployeeResponse me = currentEmployeeOrDeny("payroll.proof.submit_own");
        UUID tenantId = TenantContext.require();
        EmployeeProofOfInvestment proof = lockOwnProof(me.id(), financialYear);
        IncomeTaxDeclarationWindow window = windowOf(proof);
        boolean open = window.isProofOpenOn(today());

        // The items mirror the declared lines only while the declaration stays submitted; an officer's
        // reopen in the meantime would leave them describing lines that may since have changed. Read after
        // the proof lock: a reopen takes the same lock (ProofInProgressCheckImpl), so this sees its result.
        EmployeeInvestmentDeclaration declaration = declarationRepository
                .findByTenantIdAndId(tenantId, proof.getDeclarationId())
                .orElse(null);
        boolean declared = declaration != null && declaration.getStatus() == DeclarationStatus.SUBMITTED;
        // A proof the employee can still change is brought in line with the declaration before it is judged
        // and before it is sent. Reading the proof does this too, but an employee who reopened the
        // declaration, dropped a line and resubmitted it may submit the proof without reading it again, and
        // would otherwise send in a claim for a line that is no longer declared.
        if (declared && proof.getStatus().isEditableState()) {
            syncItems(proof, declaration, actorOf(me));
        }

        List<EmployeeProofItem> items =
                itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proof.getId());
        ProofRules.validateSubmit(
                proof.getStatus(), open, window.isPoiAttachmentMandatory(), items, documentCounts(tenantId, items));
        if (!declared) {
            throw new ProofConflictException(
                    ProofConflictException.NOT_SUBMITTED,
                    "The tax declaration is not submitted; submit it again before submitting the proof");
        }

        String actor = actorOf(me);
        Instant now = clock.instant();
        // A resubmission after a return starts a fresh review: earlier decisions are cleared, the
        // reviewers' notes stay until they are replaced.
        for (EmployeeProofItem item : items) {
            item.setStatus(ProofItemStatus.PENDING);
            item.setApprovedAmount(null);
            item.setUpdatedBy(actor);
        }
        itemRepository.saveAll(items);
        proof.setStatus(ProofStatus.SUBMITTED);
        proof.setSubmittedAt(now);
        proof.setDecidedAt(null);
        proof.setReviewerNote(null);
        proof.setUpdatedBy(actor);
        proof = proofRepository.saveAndFlush(proof);

        eventPublisher.publishEvent(
                new ProofSubmittedEvent(tenantId, proof.getId(), proof.getEmployeeId(), proof.getFinancialYear()));
        notifySubmitted(me, proof);
        log.info("Proof of investment {} submitted in tenant {}", proof.getId(), tenantId);
        return toResponse(proof);
    }

    // ── an officer's read ────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ProofResponse read(UUID employeeId, String financialYear) {
        employeeService.get(employeeId);
        return toResponse(findProof(employeeId, financialYear));
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentService.DocumentContent openDocument(
            UUID employeeId, String financialYear, UUID itemId, UUID documentId) {
        employeeService.get(employeeId);
        return openLinked(findProof(employeeId, financialYear), itemId, documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean proofOpen(String financialYear) {
        UUID tenantId = TenantContext.require();
        FinancialYear year = FinancialYear.parse(financialYear);
        return windowService.findOrCreateDefault(tenantId, year.label()).isProofOpenOn(today());
    }

    // ── proof and items ──────────────────────────────────────────────────────────────────────

    /**
     * The caller's proof, created on first read and, while it can still change, brought in line with
     * the declared lines. Needs a {@code SUBMITTED} declaration: a proof is proof of what was declared.
     */
    private EmployeeProofOfInvestment ensureProof(UUID employeeId, String financialYear, String actor) {
        UUID tenantId = TenantContext.require();
        FinancialYear year = FinancialYear.parse(financialYear);
        EmployeeInvestmentDeclaration declaration = declarationRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, year.label())
                .filter(d -> d.getStatus() == DeclarationStatus.SUBMITTED)
                .orElseThrow(() -> new ProofConflictException(
                        ProofConflictException.NOT_SUBMITTED,
                        "Submit the tax declaration for " + year.label() + " before its proof of investment"));

        UUID proofId = proofRepository
                .findIdByTenantIdAndDeclarationId(tenantId, declaration.getId())
                .orElse(null);
        if (proofId == null) {
            declarationLockRepository.lockByTenantIdAndId(tenantId, declaration.getId());
            proofId = proofRepository
                    .findIdByTenantIdAndDeclarationId(tenantId, declaration.getId())
                    .orElseGet(() -> proofRepository
                            .saveAndFlush(new EmployeeProofOfInvestment(
                                    tenantId, employeeId, declaration.getId(), year.label(), actor))
                            .getId());
        }
        // Always read the proof under its lock: whether it may be synced depends on a status another
        // request may be changing right now.
        EmployeeProofOfInvestment proof = proofRepository
                .lockByTenantIdAndId(tenantId, proofId)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment for " + year.label()));
        if (proof.getStatus().isEditableState()) {
            syncItems(proof, declaration, actor);
        }
        return proof;
    }

    private void syncItems(EmployeeProofOfInvestment proof, EmployeeInvestmentDeclaration declaration, String actor) {
        UUID tenantId = proof.getTenantId();
        Plan plan = ProofItemSync.plan(
                sourceReader.read(tenantId, declaration.getId()),
                itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proof.getId()));
        if (plan.isEmpty()) {
            return;
        }
        if (!plan.toRemove().isEmpty()) {
            // A removed line takes its files with it: the links go, the documents are soft-deleted.
            for (EmployeeProofItemDocument link :
                    documentLinkRepository.findByTenantIdAndItemIdInOrderByCreatedAtAscIdAsc(
                            tenantId, plan.toRemove())) {
                softDelete(link.getDocumentId());
            }
            documentLinkRepository.deleteByTenantIdAndItemIdIn(tenantId, plan.toRemove());
            itemRepository.deleteByTenantIdAndIdIn(tenantId, plan.toRemove());
        }
        for (ProofItemSync.Refresh refresh : plan.toRefresh()) {
            refresh.item().setDeclaredAmount(refresh.line().declaredAmount());
            refresh.item().setDescription(refresh.line().description());
            refresh.item().setUpdatedBy(actor);
            itemRepository.save(refresh.item());
        }
        List<EmployeeProofItem> created = new ArrayList<>();
        for (ProofSourceLine line : plan.toCreate()) {
            created.add(new EmployeeProofItem(
                    tenantId,
                    proof.getId(),
                    line.kind(),
                    line.lineId(),
                    line.description(),
                    line.declaredAmount(),
                    actor));
        }
        itemRepository.saveAll(created);
        itemRepository.flush();
    }

    /** The caller's proof for the year, locked for update. A caller with no proof has no item to change. */
    private EmployeeProofOfInvestment lockOwnProof(UUID employeeId, String financialYear) {
        UUID tenantId = TenantContext.require();
        FinancialYear year = FinancialYear.parse(financialYear);
        // The id first, the entity second and under the lock: see the repository note on why the
        // entity must not be loaded before it is locked.
        UUID proofId = proofRepository
                .findIdByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, year.label())
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment for " + year.label()));
        return proofRepository
                .lockByTenantIdAndId(tenantId, proofId)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment for " + year.label()));
    }

    private EmployeeProofOfInvestment findProof(UUID employeeId, String financialYear) {
        UUID tenantId = TenantContext.require();
        FinancialYear year = FinancialYear.parse(financialYear);
        return proofRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, year.label())
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment for " + year.label()));
    }

    private EmployeeProofItem requireItem(EmployeeProofOfInvestment proof, UUID itemId) {
        return itemRepository
                .findByTenantIdAndProofIdAndId(proof.getTenantId(), proof.getId(), itemId)
                .orElseThrow(() -> new ProofNotFoundException("No such item on this proof"));
    }

    /**
     * A file linked to an item of this proof. The link is looked up by item first, and the item by this
     * proof, so an id from another item or another employee is the same {@code 404} as one that never was.
     */
    private DocumentService.DocumentContent openLinked(EmployeeProofOfInvestment proof, UUID itemId, UUID documentId) {
        EmployeeProofItem item = requireItem(proof, itemId);
        EmployeeProofItemDocument link = documentLinkRepository
                .findByTenantIdAndItemIdAndDocumentId(proof.getTenantId(), item.getId(), documentId)
                .orElseThrow(() -> new ProofNotFoundException("No such file on this item"));
        try {
            return documentService.open(link.getDocumentId());
        } catch (DocumentService.NotFoundException e) {
            throw new ProofNotFoundException("No such file on this item");
        }
    }

    // ── response ─────────────────────────────────────────────────────────────────────────────

    private ProofResponse toResponse(EmployeeProofOfInvestment proof) {
        UUID tenantId = proof.getTenantId();
        IncomeTaxDeclarationWindow window = windowOf(proof);
        boolean open = window.isProofOpenOn(today());
        List<EmployeeProofItem> items =
                itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proof.getId());
        Map<UUID, List<ProofDocumentResponse>> documents = documentsOf(items);
        List<ProofItemResponse> itemResponses = items.stream()
                .map(i -> ProofItemResponse.from(i, documents.getOrDefault(i.getId(), List.of())))
                .toList();
        return new ProofResponse(
                proof.getId(),
                proof.getEmployeeId(),
                proof.getFinancialYear(),
                proof.getStatus(),
                open,
                ProofRules.isEditable(open, proof.getStatus()),
                window.getPoiDueDate(),
                window.isPoiAttachmentMandatory(),
                proof.getSubmittedAt(),
                proof.getDecidedAt(),
                proof.getReviewerNote(),
                itemResponses);
    }

    private Map<UUID, List<ProofDocumentResponse>> documentsOf(List<EmployeeProofItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        UUID tenantId = items.get(0).getTenantId();
        List<UUID> itemIds = items.stream().map(EmployeeProofItem::getId).toList();
        Map<UUID, List<ProofDocumentResponse>> byItem = new HashMap<>();
        liveLinksOf(tenantId, itemIds)
                .forEach((itemId, docs) -> byItem.put(
                        itemId,
                        docs.stream().map(ProofServiceImpl::toDocumentResponse).toList()));
        return byItem;
    }

    private Map<UUID, Long> documentCounts(UUID tenantId, List<EmployeeProofItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<UUID> itemIds = items.stream().map(EmployeeProofItem::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        liveLinksOf(tenantId, itemIds).forEach((itemId, docs) -> counts.put(itemId, (long) docs.size()));
        return counts;
    }

    /**
     * The files of these items that still exist. A link whose document was soft-deleted through core since is
     * not a file of the item: it neither counts as an attachment nor is shown, so an item whose only file was
     * deleted is not "attached" at submit.
     */
    private Map<UUID, List<DocumentResponse>> liveLinksOf(UUID tenantId, List<UUID> itemIds) {
        Map<UUID, List<DocumentResponse>> live = new HashMap<>();
        for (EmployeeProofItemDocument link :
                documentLinkRepository.findByTenantIdAndItemIdInOrderByCreatedAtAscIdAsc(tenantId, itemIds)) {
            try {
                live.computeIfAbsent(link.getItemId(), k -> new ArrayList<>())
                        .add(documentService.get(link.getDocumentId()));
            } catch (DocumentService.NotFoundException e) {
                log.debug("Proof item {} links a document that no longer exists", link.getItemId());
            }
        }
        return live;
    }

    private static ProofDocumentResponse toDocumentResponse(DocumentResponse doc) {
        return new ProofDocumentResponse(doc.id(), doc.fileName(), doc.contentType(), doc.sizeBytes(), doc.createdAt());
    }

    // ── small helpers ────────────────────────────────────────────────────────────────────────

    private IncomeTaxDeclarationWindow windowOf(EmployeeProofOfInvestment proof) {
        return windowService.findOrCreateDefault(proof.getTenantId(), proof.getFinancialYear());
    }

    private boolean isProofOpen(EmployeeProofOfInvestment proof) {
        return windowOf(proof).isProofOpenOn(today());
    }

    private LocalDate today() {
        return TaxDeclarationRules.today(clock);
    }

    private void softDelete(UUID documentId) {
        try {
            documentService.delete(documentId);
        } catch (DocumentService.NotFoundException e) {
            log.debug("Document {} was already deleted", documentId);
        }
    }

    private EmployeeResponse currentEmployeeOrDeny(String actionCode) {
        return employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(actionCode));
    }

    /** The audit actor: the employee's work email, or {@code system} when there is none. */
    private static String actorOf(EmployeeResponse employee) {
        String email = employee.workEmail();
        return email == null || email.isBlank() ? "system" : email;
    }

    private void notifySubmitted(EmployeeResponse me, EmployeeProofOfInvestment proof) {
        String name = ((me.firstName() == null ? "" : me.firstName()) + " "
                        + (me.lastName() == null ? "" : me.lastName()))
                .trim();
        UUID proofId = proof.getId();
        UUID tenantId = proof.getTenantId();
        UUID employeeId = me.id();
        Map<String, Object> values = Map.of("employee_name", name, "financial_year", proof.getFinancialYear());
        Runnable compose = () -> {
            try {
                notificationTransaction.executeWithoutResult(
                        status -> notificationService.compose(NotificationEvent.POI_SUBMITTED, employeeId, values));
            } catch (RuntimeException e) {
                // The submit is the employee's act and is recorded; a notification that cannot be composed is
                // logged (ids only, never the name) and changes nothing.
                log.warn("POI_SUBMITTED not composed for proof {} in tenant {}", proofId, tenantId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Only once the submit has committed, and in a transaction of its own: a failure inside the
            // notification service, a database error included, could otherwise mark the submit's transaction
            // rollback-only and undo what the employee did.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    compose.run();
                }
            });
        } else {
            compose.run();
        }
    }
}
