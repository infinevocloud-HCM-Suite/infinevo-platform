package com.infinevo.payroll.reimbursement;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ReimbursementClaimService} (W-35.1).
 */
@Service
public class ReimbursementClaimServiceImpl implements ReimbursementClaimService {

    private final ReimbursementClaimRepository claimRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final ApprovalService approvalService;
    private final EmployeeService employeeService;
    private final DocumentService documentService;

    public ReimbursementClaimServiceImpl(
            ReimbursementClaimRepository claimRepository,
            ReimbursementRepository reimbursementRepository,
            ApprovalService approvalService,
            EmployeeService employeeService,
            DocumentService documentService) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository must not be null");
        this.reimbursementRepository =
                Objects.requireNonNull(reimbursementRepository, "reimbursementRepository must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
    }

    @Override
    @Transactional
    public ReimbursementClaimResponse submit(ReimbursementClaimRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));

        if (request == null) {
            throw new ReimbursementClaimValidationException("Request payload must not be null");
        }

        // 1. requested_amount > 0 and scale at most 2
        BigDecimal requestedAmount = request.requestedAmount();
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReimbursementClaimValidationException("requested_amount must be greater than 0");
        }
        if (requestedAmount.scale() > 2) {
            throw new ReimbursementClaimValidationException("requested_amount scale must be at most 2");
        }

        // 2. bill_date not after today
        LocalDate billDate = request.billDate();
        if (billDate == null) {
            throw new ReimbursementClaimValidationException("bill_date must not be null");
        }
        if (billDate.isAfter(LocalDate.now())) {
            throw new ReimbursementClaimValidationException("bill_date must not be after today");
        }

        // 3. description at most 500 characters
        if (request.description() != null && request.description().length() > 500) {
            throw new ReimbursementClaimValidationException("description must be at most 500 characters");
        }

        // 4. reimbursement_id is an active, undeleted row of payroll.reimbursement in the bound tenant
        if (request.reimbursementId() == null) {
            throw new ReimbursementClaimValidationException("reimbursement_id must not be null");
        }
        Reimbursement component = reimbursementRepository
                .findByIdAndTenantIdAndDeletedFalse(request.reimbursementId(), tenantId)
                .orElseThrow(() -> new ReimbursementClaimValidationException(
                        "Reimbursement component not found: " + request.reimbursementId()));
        if (!component.isActive()) {
            throw new ReimbursementClaimValidationException(
                    "Reimbursement component is not active: " + request.reimbursementId());
        }

        // 5. document_id, when given, is of kind REIMBURSEMENT_RECEIPT whose employee is the caller
        if (request.documentId() != null) {
            try {
                DocumentResponse doc = documentService.get(request.documentId());
                if (doc.kind() != DocumentKind.REIMBURSEMENT_RECEIPT) {
                    throw new ReimbursementClaimValidationException(
                            "Document must be of kind REIMBURSEMENT_RECEIPT, found: " + doc.kind());
                }
                if (!Objects.equals(doc.employeeId(), employee.id())) {
                    throw new ReimbursementClaimValidationException(
                            "Document does not belong to caller: " + request.documentId());
                }
            } catch (DocumentService.NotFoundException e) {
                throw new ReimbursementClaimValidationException("Document not found: " + request.documentId());
            }
        }

        String actor = employee.workEmail() != null ? employee.workEmail() : "system";
        ReimbursementClaim claim = new ReimbursementClaim(
                tenantId,
                employee.id(),
                component.getId(),
                requestedAmount,
                billDate,
                request.description(),
                request.documentId(),
                actor);
        claim = claimRepository.save(claim);

        // Start approval instance with subject payroll.employee_reimbursement_request
        SubjectRef subject = new SubjectRef("payroll.employee_reimbursement_request", claim.getId());
        UUID instanceId = approvalService.start(ApprovalFlowType.REIMBURSEMENT, subject, employee.id());
        claim.setApprovalInstanceId(instanceId);
        claim = claimRepository.save(claim);

        return ReimbursementClaimResponse.from(claim, component);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReimbursementClaimResponse> listOwn() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));

        List<ReimbursementClaim> claims =
                claimRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employee.id());
        if (claims.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, Reimbursement> componentMap = loadComponents(claims);
        return claims.stream()
                .map(c -> ReimbursementClaimResponse.from(c, componentMap.get(c.getReimbursementId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReimbursementClaimResponse getOwn(UUID id) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));

        ReimbursementClaim claim = claimRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ReimbursementClaimNotFoundException("Reimbursement claim not found: " + id));

        if (!Objects.equals(claim.getEmployeeId(), employee.id())) {
            throw new ReimbursementClaimNotFoundException("Reimbursement claim not found: " + id);
        }

        Reimbursement component =
                reimbursementRepository.findById(claim.getReimbursementId()).orElse(null);
        return ReimbursementClaimResponse.from(claim, component);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReimbursementClaimResponse> list(
            UUID employeeId, ClaimStatus status, LocalDate from, LocalDate to, Pageable pageable) {
        UUID tenantId = TenantContext.require();

        Specification<ReimbursementClaim> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employeeId"), employeeId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("billDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("billDate"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ReimbursementClaim> page = claimRepository.findAll(spec, pageable);
        Map<UUID, Reimbursement> componentMap = loadComponents(page.getContent());
        return page.map(c -> ReimbursementClaimResponse.from(c, componentMap.get(c.getReimbursementId())));
    }

    @Override
    @Transactional(readOnly = true)
    public ReimbursementClaimResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        ReimbursementClaim claim = claimRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ReimbursementClaimNotFoundException("Reimbursement claim not found: " + id));

        Reimbursement component =
                reimbursementRepository.findById(claim.getReimbursementId()).orElse(null);
        return ReimbursementClaimResponse.from(claim, component);
    }

    private Map<UUID, Reimbursement> loadComponents(List<ReimbursementClaim> claims) {
        Set<UUID> ids =
                claims.stream().map(ReimbursementClaim::getReimbursementId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return reimbursementRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Reimbursement::getId, Function.identity()));
    }
}
