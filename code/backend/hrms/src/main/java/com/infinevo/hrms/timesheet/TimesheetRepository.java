package com.infinevo.hrms.timesheet;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** The caller's live timesheet for a week: one at most, as a cancelled one does not hold the week. */
    Optional<Timesheet> findByTenantIdAndEmployeeIdAndWeekStartDateAndStatusNot(
            UUID tenantId, UUID employeeId, LocalDate weekStartDate, TimesheetStatus status);

    boolean existsByTenantIdAndEmployeeIdAndWeekStartDateAndStatusNot(
            UUID tenantId, UUID employeeId, LocalDate weekStartDate, TimesheetStatus status);

    /** The caller's timesheets whose week starts between the two dates, inclusive, newest week first. */
    List<Timesheet> findByTenantIdAndEmployeeIdAndWeekStartDateBetweenOrderByWeekStartDateDesc(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);
}
