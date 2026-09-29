package com.infinevo.core.report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** {@code core.report_schedule}, always read with the tenant as well as the id (W-23.2). */
@Transactional(readOnly = true)
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {

    List<ReportSchedule> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    List<ReportSchedule> findByTenantIdAndIsActiveTrue(UUID tenantId);

    Optional<ReportSchedule> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Claims a due schedule before it runs: stamps {@code last_run_at} and {@code RUNNING} only if
     * {@code last_run_at} is still what the evaluator read. {@code 1} means this run owns it; {@code 0}
     * means another run already took it. Claiming first means a crash part-way through leaves the
     * schedule marked as run for the period — a missed report, never a second copy to every recipient.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE core.report_schedule SET last_run_at = :now, last_run_status = 'RUNNING',"
                    + " updated_at = :now, updated_by = 'worker'"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND last_run_at IS NOT DISTINCT FROM CAST(:previous AS timestamptz)",
            nativeQuery = true)
    int claim(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("previous") Instant previous,
            @Param("now") Instant now);
}
