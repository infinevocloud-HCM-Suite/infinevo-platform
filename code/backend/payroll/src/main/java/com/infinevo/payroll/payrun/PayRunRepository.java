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

    /** W-30.2: only a regular run blocks another regular run for the period; off-cycle runs are many. */
    boolean existsByTenantIdAndPeriodAndRunTypeAndStatusNot(
            UUID tenantId, String period, PayRunType runType, PayRunStatus status);

    Page<PayRun> findByTenantId(UUID tenantId, Pageable pageable);

    Page<PayRun> findByTenantIdAndStatus(UUID tenantId, PayRunStatus status, Pageable pageable);

    Page<PayRun> findByTenantIdAndRunType(UUID tenantId, PayRunType runType, Pageable pageable);

    Page<PayRun> findByTenantIdAndStatusAndRunType(
            UUID tenantId, PayRunStatus status, PayRunType runType, Pageable pageable);

    /** {@code SELECT … FOR UPDATE}: two computes of one run serialise, and the second sees {@code COMPUTING}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PayRun r WHERE r.id = :id AND r.tenantId = :tenantId")
    Optional<PayRun> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT MIN(r.period) FROM PayRun r WHERE r.tenantId = :tenantId AND r.runType = :runType "
            + "AND r.status != :excludedStatus AND r.period >= :fromPeriod AND r.period <= :toPeriod")
    Optional<String> findEarliestPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("runType") PayRunType runType,
            @Param("excludedStatus") PayRunStatus excludedStatus,
            @Param("fromPeriod") String fromPeriod,
            @Param("toPeriod") String toPeriod);

    /** The tenant's earliest run of a type, ignoring one status (W-38.3 §3: first regular run not cancelled). */
    Optional<PayRun> findFirstByTenantIdAndRunTypeAndStatusNotOrderByPeriodAsc(
            UUID tenantId, PayRunType runType, PayRunStatus status);
}
