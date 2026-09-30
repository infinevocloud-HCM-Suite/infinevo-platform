package com.infinevo.payroll.payrun;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder takes {@code tenantId} (DEBT-022); row-level security is the backstop, not the filter. */
public interface PayRunRepository extends JpaRepository<PayRun, UUID> {

    Optional<PayRun> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndPeriodAndStatusNot(UUID tenantId, String period, PayRunStatus status);

    Page<PayRun> findByTenantId(UUID tenantId, Pageable pageable);

    Page<PayRun> findByTenantIdAndStatus(UUID tenantId, PayRunStatus status, Pageable pageable);
}
