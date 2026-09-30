package com.infinevo.payroll.payrun;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes {@code tenantId} (DEBT-022); row-level security is the backstop, not the filter. */
public interface PayRunRepository extends JpaRepository<PayRun, UUID> {

    Optional<PayRun> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndPeriodAndStatusNot(UUID tenantId, String period, PayRunStatus status);

    Page<PayRun> findByTenantId(UUID tenantId, Pageable pageable);

    Page<PayRun> findByTenantIdAndStatus(UUID tenantId, PayRunStatus status, Pageable pageable);

    /** {@code SELECT … FOR UPDATE}: two computes of one run serialise, and the second sees {@code COMPUTING}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PayRun r WHERE r.id = :id AND r.tenantId = :tenantId")
    Optional<PayRun> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
