package com.infinevo.payroll.payrun;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
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

    /** A regular run for the period in one of the given statuses — "is this month already paid?" */
    boolean existsByTenantIdAndPeriodAndRunTypeAndStatusIn(
            UUID tenantId, String period, PayRunType runType, Collection<PayRunStatus> statuses);

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

    /**
     * D-147: the earliest period in the range with a run of the type that is still open, i.e. not in one of
     * {@code closedStatuses}. A new TDS record starts there so that month's run gets its tax line when computed.
     */
    @Query("SELECT MIN(r.period) FROM PayRun r WHERE r.tenantId = :tenantId AND r.runType = :runType "
            + "AND r.status NOT IN :closedStatuses AND r.period >= :fromPeriod AND r.period <= :toPeriod")
    Optional<String> findEarliestOpenPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("runType") PayRunType runType,
            @Param("closedStatuses") Collection<PayRunStatus> closedStatuses,
            @Param("fromPeriod") String fromPeriod,
            @Param("toPeriod") String toPeriod);

    /** The tenant's earliest run of a type, ignoring one status (W-38.3 §3: first regular run not cancelled). */
    Optional<PayRun> findFirstByTenantIdAndRunTypeAndStatusNotOrderByPeriodAsc(
            UUID tenantId, PayRunType runType, PayRunStatus status);

    /**
     * Distinct regular periods with PAID status in the given period range (W-36.4 §4). Form 16 adds
     * the tenant's imported periods to these before deciding {@code final} (W-38.2 §3).
     */
    @Query(
            """
            SELECT DISTINCT pr.period
            FROM PayRun pr
            WHERE pr.tenantId = :tenantId
              AND pr.status = com.infinevo.payroll.payrun.PayRunStatus.PAID
              AND pr.runType = com.infinevo.payroll.payrun.PayRunType.REGULAR
              AND pr.period >= :startPeriod
              AND pr.period <= :endPeriod
            """)
    List<String> findPaidRegularPeriods(
            @Param("tenantId") UUID tenantId,
            @Param("startPeriod") String startPeriod,
            @Param("endPeriod") String endPeriod);

    /**
     * D-145: distinct regular periods in the range whose run is computed, approved or paid - months whose
     * TDS line is already counted in the year to date. A draft, locked, computing, failed or cancelled run
     * leaves its month still to be paid.
     */
    @Query(
            """
            SELECT DISTINCT pr.period
            FROM PayRun pr
            WHERE pr.tenantId = :tenantId
              AND pr.runType = com.infinevo.payroll.payrun.PayRunType.REGULAR
              AND pr.status IN (
                  com.infinevo.payroll.payrun.PayRunStatus.COMPUTED,
                  com.infinevo.payroll.payrun.PayRunStatus.APPROVED,
                  com.infinevo.payroll.payrun.PayRunStatus.PAID
              )
              AND pr.period >= :startPeriod
              AND pr.period <= :endPeriod
            """)
    List<String> findCountedRegularPeriods(
            @Param("tenantId") UUID tenantId,
            @Param("startPeriod") String startPeriod,
            @Param("endPeriod") String endPeriod);
}
