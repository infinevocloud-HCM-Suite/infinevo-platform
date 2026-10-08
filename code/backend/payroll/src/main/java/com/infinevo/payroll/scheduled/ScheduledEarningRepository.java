package com.infinevo.payroll.scheduled;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes {@code tenantId} (DEBT-022); row-level security is the backstop, not the filter. */
public interface ScheduledEarningRepository extends JpaRepository<ScheduledEarning, UUID> {

    Optional<ScheduledEarning> findByTenantIdAndId(UUID tenantId, UUID id);

    /** One employee's rows, newest first-period first, every status included (the Salary tab's history). */
    List<ScheduledEarning> findByTenantIdAndEmployeeIdOrderByFirstPeriodDescCreatedAtDesc(
            UUID tenantId, UUID employeeId);

    /**
     * The {@code SCHEDULED} rows that may have an instalment due in the period starting
     * {@code periodStart} — every one whose first period is on or before it, served by the
     * {@code (tenant_id, status, first_period)} index. The caller keeps only those whose
     * <em>next</em> period is the one asked for ({@link ScheduledEarning#isDueIn}); that depends on
     * {@code paid_instalments} and is cheaper to decide in Java than in SQL.
     */
    @Query(
            "SELECT s FROM ScheduledEarning s WHERE s.tenantId = :tenantId AND s.status = com.infinevo.payroll.scheduled.ScheduledEarningStatus.SCHEDULED"
                    + " AND s.firstPeriod <= :periodStart ORDER BY s.createdAt")
    List<ScheduledEarning> findCandidatesDue(
            @Param("tenantId") UUID tenantId, @Param("periodStart") LocalDate periodStart);

    /** The employee's rows still able to pay out — cancelled together when the employee is terminated. */
    List<ScheduledEarning> findByTenantIdAndEmployeeIdAndStatusIn(
            UUID tenantId, UUID employeeId, List<ScheduledEarningStatus> statuses);
}
