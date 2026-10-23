package com.infinevo.core.wfh;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link WfhAllocation} entities (W-74).
 */
@Repository
public interface WfhAllocationRepository extends JpaRepository<WfhAllocation, UUID> {

    Optional<WfhAllocation> findByTenantIdAndEmployeeIdAndWfhYear(UUID tenantId, UUID employeeId, String wfhYear);

    Optional<WfhAllocation> findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
            UUID tenantId, UUID employeeId, LocalDate asOfStart, LocalDate asOfEnd);

    List<WfhAllocation> findByTenantIdAndEmployeeIdOrderByYearStartDateDesc(UUID tenantId, UUID employeeId);

    List<WfhAllocation> findByTenantIdAndWfhYear(UUID tenantId, String wfhYear);

    List<WfhAllocation> findByTenantId(UUID tenantId);

    @Query("SELECT COUNT(a) > 0 FROM WfhAllocation a "
            + "WHERE a.tenantId = :tenantId AND a.employeeId = :employeeId "
            + "AND a.yearStartDate <= :yearEnd AND a.yearEndDate >= :yearStart")
    boolean existsOverlappingAllocation(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd);
}
