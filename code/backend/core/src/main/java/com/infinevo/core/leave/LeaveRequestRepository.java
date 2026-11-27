package com.infinevo.core.leave;

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
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LeaveRequest} (W-16.3).
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    Optional<LeaveRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT r FROM LeaveRequest r WHERE r.tenantId = :tenantId "
            + "AND r.employeeId = :employeeId "
            + "AND r.status IN :statuses "
            + "AND r.fromDate <= :toDate "
            + "AND r.toDate >= :fromDate "
            + "AND (:excludeId IS NULL OR r.id != :excludeId)")
    List<LeaveRequest> findOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<LeaveRequestStatus> statuses,
            @Param("excludeId") UUID excludeId);

    /** No date filter on one side: the search runs with this as that side's bound. */
    LocalDate OPEN_FROM = LocalDate.of(1, 1, 1);

    /** See {@link #OPEN_FROM}. */
    LocalDate OPEN_TO = LocalDate.of(9999, 12, 31);

    /**
     * Requests overlapping {@code [fromDate, toDate]}, newest start first; a {@code null} date leaves that side open.
     * The stored query always gets two dates: an "{@code :date IS NULL OR ...}" filter cannot be typed by PostgreSQL
     * when a date is bound ({@code 42P18}, found by W-114.17, fixed in W-114.19).
     */
    default Page<LeaveRequest> search(
            UUID tenantId,
            UUID employeeId,
            LeaveRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        return searchBetween(
                tenantId,
                employeeId,
                status,
                fromDate == null ? OPEN_FROM : fromDate,
                toDate == null ? OPEN_TO : toDate,
                pageable);
    }

    /** As {@link #search}, for a set of employees. */
    default Page<LeaveRequest> searchForEmployees(
            UUID tenantId,
            Collection<UUID> employeeIds,
            LeaveRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        return searchForEmployeesBetween(
                tenantId,
                employeeIds,
                status,
                fromDate == null ? OPEN_FROM : fromDate,
                toDate == null ? OPEN_TO : toDate,
                pageable);
    }

    @Query("SELECT r FROM LeaveRequest r WHERE r.tenantId = :tenantId "
            + "AND (:employeeId IS NULL OR r.employeeId = :employeeId) "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND r.toDate >= :fromDate "
            + "AND r.fromDate <= :toDate "
            + "ORDER BY r.fromDate DESC")
    Page<LeaveRequest> searchBetween(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("status") LeaveRequestStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("SELECT r FROM LeaveRequest r WHERE r.tenantId = :tenantId "
            + "AND r.employeeId IN :employeeIds "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND r.toDate >= :fromDate "
            + "AND r.fromDate <= :toDate "
            + "ORDER BY r.fromDate DESC")
    Page<LeaveRequest> searchForEmployeesBetween(
            @Param("tenantId") UUID tenantId,
            @Param("employeeIds") Collection<UUID> employeeIds,
            @Param("status") LeaveRequestStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    List<LeaveRequest> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<LeaveRequest> findByTenantIdAndEmployeeIdAndLeaveTypeIdAndStatus(
            UUID tenantId, UUID employeeId, UUID leaveTypeId, LeaveRequestStatus status);
}
