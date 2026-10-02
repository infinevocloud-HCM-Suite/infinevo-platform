package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.ProofInProgressCheck;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers {@link ProofInProgressCheck} from the proof table (W-34.1). Tenant-bound.
 *
 * <p>The proof row is read under the same {@code FOR UPDATE} lock submit takes, inside the caller's
 * transaction, so a reopen and a submit on one declaration queue behind each other: whichever runs second
 * sees the other's committed result (submit refuses a draft declaration; reopen refuses a submitted proof)
 * and a submitted proof can no longer end up on a draft declaration.
 */
@Component
public class ProofInProgressCheckImpl implements ProofInProgressCheck {

    private final EmployeeProofOfInvestmentRepository proofRepository;

    public ProofInProgressCheckImpl(EmployeeProofOfInvestmentRepository proofRepository) {
        this.proofRepository = Objects.requireNonNull(proofRepository, "proofRepository must not be null");
    }

    @Override
    @Transactional
    public boolean isProofInProgress(UUID tenantId, UUID declarationId) {
        // The id first, then the entity under the lock: see the repository note on loading before locking.
        return proofRepository
                .findIdByTenantIdAndDeclarationId(tenantId, declarationId)
                .flatMap(id -> proofRepository.lockByTenantIdAndId(tenantId, id))
                .map(p -> p.getStatus() == ProofStatus.SUBMITTED || p.getStatus() == ProofStatus.APPROVED)
                .orElse(false);
    }
}
