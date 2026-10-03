package com.infinevo.hrms.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link ClockSession} (W-40.3).
 *
 * <p>Every finder takes {@code tenantId} (DEBT-022 fixed, spec §4, §11).
 */
@Repository
public interface ClockSessionRepository extends JpaRepository<ClockSession, UUID> {

    /** Finds the single open session for an employee in the bound tenant, if any. */
    Optional<ClockSession> findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(
            UUID tenantId, UUID employeeId);

    /** Finds all sessions for an employee on a date, ordered by clock in time ascending. */
    List<ClockSession> findByTenantIdAndEmployeeIdAndAttendanceDateOrderByClockInAtAsc(
            UUID tenantId, UUID employeeId, LocalDate attendanceDate);

    /**
     * Finds closed, non-voided sessions for an employee on a date used for derivation.
     */
    List<ClockSession>
            findByTenantIdAndEmployeeIdAndAttendanceDateAndVoidedAtIsNullAndClockOutAtIsNotNullOrderByClockInAtAsc(
                    UUID tenantId, UUID employeeId, LocalDate attendanceDate);

    /** Caller's sessions in a date range, newest first. */
    List<ClockSession> findByTenantIdAndEmployeeIdAndAttendanceDateBetweenOrderByClockInAtDesc(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);

    /** Tenant's sessions in a date range, newest first. */
    List<ClockSession> findByTenantIdAndAttendanceDateBetweenOrderByClockInAtDesc(
            UUID tenantId, LocalDate from, LocalDate to);
}
