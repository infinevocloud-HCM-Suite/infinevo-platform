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

    @Query("SELECT r FROM LeaveRequest r WHERE r.tenantId = :tenantId "
            + "AND (:employeeId IS NULL OR r.employeeId = :employeeId) "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND (:fromDate IS NULL OR r.toDate >= :fromDate) "
            + "AND (:toDate IS NULL OR r.fromDate <= :toDate) "
            + "ORDER BY r.fromDate DESC")
    Page<LeaveRequest> search(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("status") LeaveRequestStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("SELECT r FROM LeaveRequest r WHERE r.tenantId = :tenantId "
            + "AND r.employeeId IN :employeeIds "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND (:fromDate IS NULL OR r.toDate >= :fromDate) "
            + "AND (:toDate IS NULL OR r.fromDate <= :toDate) "
            + "ORDER BY r.fromDate DESC")
    Page<LeaveRequest> searchForEmployees(
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
