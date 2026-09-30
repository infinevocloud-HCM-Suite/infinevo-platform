package com.infinevo.payroll.reimbursement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring Data JPA repository for {@link ReimbursementClaim} (W-35.1).
 * All queries are scoped by tenant_id (DEBT-022).
 */
public interface ReimbursementClaimRepository
        extends JpaRepository<ReimbursementClaim, UUID>, JpaSpecificationExecutor<ReimbursementClaim> {

    Optional<ReimbursementClaim> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ReimbursementClaim> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);

    Optional<ReimbursementClaim> findByTenantIdAndApprovalInstanceId(UUID tenantId, UUID approvalInstanceId);

    Page<ReimbursementClaim> findAll(Specification<ReimbursementClaim> spec, Pageable pageable);
}
