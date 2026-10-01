package com.infinevo.payroll.payrun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes {@code tenantId} (DEBT-022) — the legacy {@code findByPayrunId} did not. */
public interface EmployeePayRunRepository extends JpaRepository<EmployeePayRun, UUID> {

    Page<EmployeePayRun> findByTenantIdAndPayrunId(UUID tenantId, UUID payrunId, Pageable pageable);

    Page<EmployeePayRun> findByTenantIdAndPayrunIdAndInclusionStatus(
            UUID tenantId, UUID payrunId, InclusionStatus inclusionStatus, Pageable pageable);

    List<EmployeePayRun> findAllByTenantIdAndPayrunIdAndInclusionStatus(
            UUID tenantId, UUID payrunId, InclusionStatus inclusionStatus);

    Optional<EmployeePayRun> findByTenantIdAndPayrunIdAndEmployeeId(UUID tenantId, UUID payrunId, UUID employeeId);

    Optional<EmployeePayRun> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT r FROM EmployeePayRun r, PayRun p WHERE r.payrunId = p.id AND r.tenantId = :tenantId"
            + " AND r.employeeId = :employeeId AND r.inclusionStatus = com.infinevo.payroll.payrun.InclusionStatus.INCLUDED"
            + " AND p.status = com.infinevo.payroll.payrun.PayRunStatus.PAID ORDER BY p.period DESC")
    Page<EmployeePayRun> findOwnPaidRuns(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, Pageable pageable);

    /**
     * A resumed attempt keeps what the abandoned one finished (W-29.4 §3): the included rows the
     * previous attempt computed without error move to the new attempt number and are not recomputed.
     * Rows that failed are left behind, so the new attempt tries them again.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EmployeePayRun r SET r.computedAttempt = :next"
            + " WHERE r.tenantId = :tenantId AND r.payrunId = :payrunId"
            + " AND r.inclusionStatus = com.infinevo.payroll.payrun.InclusionStatus.INCLUDED"
            + " AND r.computedAttempt = :previous AND r.computationError IS NULL")
    int carryForward(
            @Param("tenantId") UUID tenantId,
            @Param("payrunId") UUID payrunId,
            @Param("previous") int previous,
            @Param("next") int next);
}
