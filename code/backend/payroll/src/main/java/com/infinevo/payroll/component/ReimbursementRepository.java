package com.infinevo.payroll.component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Reimbursement} entities (W-26.1).
 */
public interface ReimbursementRepository extends JpaRepository<Reimbursement, UUID> {

    Optional<Reimbursement> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    List<Reimbursement> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    List<Reimbursement> findAllByTenantIdAndActiveAndDeletedFalse(UUID tenantId, boolean active);

    List<Reimbursement> findAllByTenantIdAndActiveAndDeletedFalseAndFbpComponentTrue(UUID tenantId, boolean active);

    Optional<Reimbursement> findByTenantIdAndCodeAndDeletedFalse(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, UUID id);
}
