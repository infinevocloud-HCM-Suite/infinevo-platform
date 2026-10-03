package com.infinevo.hrms.timesheet;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code hrms.timesheet} (W-42.1).
 *
 * <p>Every finder takes {@code tenantId} (DEBT-022), with row-level security behind it. The owner-scoped finders take
 * the employee too: a timesheet that is not the caller's is "not found", so ids do not leak.
 */
@Transactional(readOnly = true)
public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {

    Optional<Timesheet> findByIdAndTenantIdAndEmployeeId(UUID id, UUID tenantId, UUID employeeId);

    /** A timesheet of any employee in the tenant: for the review reads, which decide access themselves (W-42.4). */
    Optional<Timesheet> findByIdAndTenantId(UUID id, UUID tenantId);

    /** The caller's live timesheet for a week: one at most, as a cancelled one does not hold the week. */
    Optional<Timesheet> findByTenantIdAndEmployeeIdAndWeekStartDateAndStatusNot(
            UUID tenantId, UUID employeeId, LocalDate weekStartDate, TimesheetStatus status);

    boolean existsByTenantIdAndEmployeeIdAndWeekStartDateAndStatusNot(
            UUID tenantId, UUID employeeId, LocalDate weekStartDate, TimesheetStatus status);

    /** The caller's timesheets whose week starts between the two dates, inclusive, newest week first. */
    List<Timesheet> findByTenantIdAndEmployeeIdAndWeekStartDateBetweenOrderByWeekStartDateDesc(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);

    // ── Review lists (W-42.4) ────────────────────────────────────────────────────────────────
    //
    // Each returns one page of week ids, newest week first, then employee; the weeks are loaded in one query after.
    // The status and week filters are in the SQL, not applied to a loaded list (DEBT-022). An 'optional' filter is a
    // flag plus a value that is always bound, as in ProjectRepository: an untyped null does not bind on PostgreSQL.

    /** Every week in the tenant whose status is one of {@code statuses}: HR's list. */
    @Query(
            """
        SELECT t.id FROM Timesheet t
        WHERE t.tenantId = :tenantId
          AND t.status IN :statuses
          AND t.weekStartDate >= :from AND t.weekStartDate <= :to
          AND (:anyEmployee = true OR t.employeeId = :employeeId)
          AND (:anyProject = true OR EXISTS (
                SELECT 1 FROM TimesheetProjectEntry e
                WHERE e.timesheet = t AND e.tenantId = :tenantId AND e.projectId = :projectId))
        ORDER BY t.weekStartDate DESC, t.employeeId ASC, t.id ASC
    """)
    Page<UUID> pageAll(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") Collection<TimesheetStatus> statuses,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("anyEmployee") boolean anyEmployee,
            @Param("employeeId") UUID employeeId,
            @Param("anyProject") boolean anyProject,
            @Param("projectId") UUID projectId,
            Pageable pageable);

    /** The weeks of the given employees (a manager's direct reports). */
    @Query(
            """
        SELECT t.id FROM Timesheet t
        WHERE t.tenantId = :tenantId
          AND t.employeeId IN :employeeIds
          AND t.status IN :statuses
          AND t.weekStartDate >= :from AND t.weekStartDate <= :to
        ORDER BY t.weekStartDate DESC, t.employeeId ASC, t.id ASC
    """)
    Page<UUID> pageForEmployees(
            @Param("tenantId") UUID tenantId,
            @Param("employeeIds") Collection<UUID> employeeIds,
            @Param("statuses") Collection<TimesheetStatus> statuses,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /** The weeks with at least one submitted-or-later line on one of the given projects (a project manager's). */
    @Query(
            """
        SELECT t.id FROM Timesheet t
        WHERE t.tenantId = :tenantId
          AND t.status IN :statuses
          AND t.weekStartDate >= :from AND t.weekStartDate <= :to
          AND EXISTS (
                SELECT 1 FROM TimesheetProjectEntry e
                WHERE e.timesheet = t AND e.tenantId = :tenantId
                  AND e.projectId IN :projectIds
                  AND e.status <> com.infinevo.hrms.timesheet.TimesheetStatus.DRAFT)
        ORDER BY t.weekStartDate DESC, t.employeeId ASC, t.id ASC
    """)
    Page<UUID> pageOnProjects(
            @Param("tenantId") UUID tenantId,
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("statuses") Collection<TimesheetStatus> statuses,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /** The weeks for a page of ids, with their project lines in the same query; tasks and days load in batches. */
    @Query(
            """
        SELECT DISTINCT t FROM Timesheet t LEFT JOIN FETCH t.projects
        WHERE t.tenantId = :tenantId AND t.id IN :ids
    """)
    List<Timesheet> findAllWithProjects(@Param("tenantId") UUID tenantId, @Param("ids") Collection<UUID> ids);
}
