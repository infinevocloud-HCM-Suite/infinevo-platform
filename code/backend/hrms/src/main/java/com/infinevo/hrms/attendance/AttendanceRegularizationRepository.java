package com.infinevo.hrms.attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AttendanceRegularization} (W-40.4). Every finder takes {@code tenantId}.
 */
@Repository
public interface AttendanceRegularizationRepository extends JpaRepository<AttendanceRegularization, UUID> {

    Optional<AttendanceRegularization> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Whether the employee already has a request in the given status for the date (the pending duplicate rule). */
    boolean existsByTenantIdAndEmployeeIdAndAttendanceDateAndStatus(
            UUID tenantId, UUID employeeId, LocalDate attendanceDate, RegularizationStatus status);

    /** Requests in the given statuses whose date falls in the range: the monthly limit counts these. */
    long countByTenantIdAndEmployeeIdAndAttendanceDateBetweenAndStatusIn(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to, Collection<RegularizationStatus> statuses);

    /** One employee's requests in a date range, newest first. */
    List<AttendanceRegularization>
            findByTenantIdAndEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                    UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);

    /** One employee's requests in a date range and status, newest first. */
    List<AttendanceRegularization>
            findByTenantIdAndEmployeeIdAndStatusAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                    UUID tenantId, UUID employeeId, RegularizationStatus status, LocalDate from, LocalDate to);

    /** The tenant's requests in a date range, newest first. */
    List<AttendanceRegularization> findByTenantIdAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
            UUID tenantId, LocalDate from, LocalDate to);

    /** The tenant's requests in a date range and status, newest first. */
    List<AttendanceRegularization>
            findByTenantIdAndStatusAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                    UUID tenantId, RegularizationStatus status, LocalDate from, LocalDate to);
}
