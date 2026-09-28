package com.infinevo.core.leave;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link LeaveAllocation} entities (W-16.2).
 */
@Repository
public interface LeaveAllocationRepository extends JpaRepository<LeaveAllocation, UUID> {

    Optional<LeaveAllocation> findByTenantIdAndEmployeeIdAndLeaveTypeIdAndLeaveYear(
            UUID tenantId, UUID employeeId, UUID leaveTypeId, String leaveYear);

    Optional<LeaveAllocation>
            findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                    UUID tenantId, UUID employeeId, UUID leaveTypeId, LocalDate asOf1, LocalDate asOf2);

    List<LeaveAllocation> findByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
            UUID tenantId, UUID employeeId, LocalDate asOf1, LocalDate asOf2);

    List<LeaveAllocation> findByTenantIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
            UUID tenantId, UUID leaveTypeId, LocalDate asOf1, LocalDate asOf2);

    List<LeaveAllocation> findByTenantIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
            UUID tenantId, LocalDate asOf1, LocalDate asOf2);

    List<LeaveAllocation> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<LeaveAllocation> findByTenantId(UUID tenantId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT a.tenantId FROM LeaveAllocation a")
    List<UUID> findDistinctTenantIds();
}
