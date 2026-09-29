package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link LeaveConsumption} (W-16.4a).
 */
public interface LeaveConsumptionRepository extends JpaRepository<LeaveConsumption, UUID> {

    boolean existsByTenantIdAndLeaveRequestIdAndReversesIdIsNull(UUID tenantId, UUID leaveRequestId);

    List<LeaveConsumption> findByTenantIdAndEmployeeIdAndPeriodStartingWithOrderByConsumedOnAscCreatedAtAsc(
            UUID tenantId, UUID employeeId, String yearPrefix);

    List<LeaveConsumption> findByTenantIdAndEmployeeIdOrderByConsumedOnAscCreatedAtAsc(UUID tenantId, UUID employeeId);

    List<LeaveConsumption> findByTenantIdAndLeaveRequestId(UUID tenantId, UUID leaveRequestId);

    Optional<LeaveConsumption> findFirstByTenantIdAndLeaveRequestIdAndReversesIdIsNull(
            UUID tenantId, UUID leaveRequestId);

    @Query("SELECT COALESCE(SUM(lc.consumedDays), 0) FROM LeaveConsumption lc "
            + "JOIN LeaveAllocation la ON lc.allocationId = la.id "
            + "WHERE lc.tenantId = :tenantId AND lc.employeeId = :employeeId "
            + "AND la.leaveTypeId = :leaveTypeId AND lc.period LIKE :yearPrefix%")
    BigDecimal sumConsumedDaysByEmployeeAndTypeAndYear(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("leaveTypeId") UUID leaveTypeId,
            @Param("yearPrefix") String yearPrefix);

    @Query("SELECT COALESCE(SUM(lc.consumedDays), 0) FROM LeaveConsumption lc "
            + "WHERE lc.tenantId = :tenantId AND lc.allocationId = :allocationId")
    BigDecimal sumConsumedDaysByAllocation(@Param("tenantId") UUID tenantId, @Param("allocationId") UUID allocationId);
}
