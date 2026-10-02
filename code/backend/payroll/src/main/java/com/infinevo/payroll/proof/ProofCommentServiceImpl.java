package com.infinevo.payroll.proof;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ProofCommentService} (W-34.2).
 *
 * <p>Enforces flat append-only comments, validates comment body length (1-1000),
 * strictly derives authorRole server-side, and guarantees that employees cannot access
 * or comment on items belonging to other employees' declarations/proofs.
 */
@Service
@Transactional
public class ProofCommentServiceImpl implements ProofCommentService {

    private final EmployeeProofOfInvestmentRepository proofRepository;
    private final EmployeeProofItemRepository itemRepository;
    private final EmployeeProofItemCommentRepository commentRepository;
    private final EmployeeService employeeService;

    public ProofCommentServiceImpl(
            EmployeeProofOfInvestmentRepository proofRepository,
            EmployeeProofItemRepository itemRepository,
            EmployeeProofItemCommentRepository commentRepository,
            EmployeeService employeeService) {
        this.proofRepository = Objects.requireNonNull(proofRepository, "proofRepository must not be null");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository must not be null");
        this.commentRepository = Objects.requireNonNull(commentRepository, "commentRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProofCommentResponse> listForReviewer(UUID proofId, UUID itemId) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(proofId, "proofId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");

        proofRepository
                .findByTenantIdAndId(tenantId, proofId)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment found with id " + proofId));

        itemRepository
                .findByTenantIdAndProofIdAndId(tenantId, proofId, itemId)
                .orElseThrow(() -> new ProofNotFoundException("No proof item found with id " + itemId));

        return commentRepository.findByTenantIdAndItemIdOrderByCreatedAtAscIdAsc(tenantId, itemId).stream()
                .map(ProofCommentResponse::from)
                .toList();
    }

    @Override
    public ProofCommentResponse addForReviewer(UUID proofId, UUID itemId, ProofCommentRequest request) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(proofId, "proofId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        validateBody(request.body());

        EmployeeResponse reviewer = employeeService
                .currentEmployee()
                .orElseThrow(() -> new PermissionDeniedException("payroll.proof.review"));

        proofRepository
                .findByTenantIdAndId(tenantId, proofId)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment found with id " + proofId));

        itemRepository
                .findByTenantIdAndProofIdAndId(tenantId, proofId, itemId)
                .orElseThrow(() -> new ProofNotFoundException("No proof item found with id " + itemId));

        EmployeeProofItemComment comment = new EmployeeProofItemComment(
                tenantId,
                itemId,
                reviewer.id(),
                ProofCommentRole.REVIEWER,
                request.body().trim(),
                actorOf(reviewer));

        return ProofCommentResponse.from(commentRepository.save(comment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProofCommentResponse> listForOwn(String financialYear, UUID itemId) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");

        EmployeeResponse me = employeeService
                .currentEmployee()
                .orElseThrow(() -> new PermissionDeniedException("payroll.proof.read_own"));

        EmployeeProofOfInvestment proof = proofRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, me.id(), financialYear)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment found for " + financialYear));

        itemRepository
                .findByTenantIdAndProofIdAndId(tenantId, proof.getId(), itemId)
                .orElseThrow(() -> new ProofNotFoundException("No proof item found with id " + itemId));

        return commentRepository.findByTenantIdAndItemIdOrderByCreatedAtAscIdAsc(tenantId, itemId).stream()
                .map(ProofCommentResponse::from)
                .toList();
    }

    @Override
    public ProofCommentResponse addForOwn(String financialYear, UUID itemId, ProofCommentRequest request) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        validateBody(request.body());

        EmployeeResponse me = employeeService
                .currentEmployee()
                .orElseThrow(() -> new PermissionDeniedException("payroll.proof.submit_own"));

        EmployeeProofOfInvestment proof = proofRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, me.id(), financialYear)
                .orElseThrow(() -> new ProofNotFoundException("No proof of investment found for " + financialYear));

        itemRepository
                .findByTenantIdAndProofIdAndId(tenantId, proof.getId(), itemId)
                .orElseThrow(() -> new ProofNotFoundException("No proof item found with id " + itemId));

        EmployeeProofItemComment comment = new EmployeeProofItemComment(
                tenantId,
                itemId,
                me.id(),
                ProofCommentRole.EMPLOYEE,
                request.body().trim(),
                actorOf(me));

        return ProofCommentResponse.from(commentRepository.save(comment));
    }

    private static void validateBody(String body) {
        if (body == null || body.trim().isEmpty() || body.length() > 1000) {
            throw new ProofValidationException("Comment body must be between 1 and 1000 characters");
        }
    }

    private static String actorOf(EmployeeResponse employee) {
        String email = employee.workEmail();
        return email == null || email.isBlank() ? "system" : email;
    }
}
