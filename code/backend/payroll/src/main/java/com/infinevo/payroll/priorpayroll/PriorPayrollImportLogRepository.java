package com.infinevo.payroll.priorpayroll;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PriorPayrollImportLog} (W-38.1 §4).
 */
@Repository
public interface PriorPayrollImportLogRepository extends JpaRepository<PriorPayrollImportLog, UUID> {

    Optional<PriorPayrollImportLog> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<PriorPayrollImportLog> findByTenantIdOrderByStartedAtDesc(UUID tenantId, Pageable pageable);
}
