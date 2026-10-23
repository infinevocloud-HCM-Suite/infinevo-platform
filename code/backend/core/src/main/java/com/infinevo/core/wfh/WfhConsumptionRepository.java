package com.infinevo.core.wfh;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link WfhConsumption} entities (W-74).
 */
@Repository
public interface WfhConsumptionRepository extends JpaRepository<WfhConsumption, UUID> {

    List<WfhConsumption> findByTenantIdAndEmployeeIdAndPeriod(UUID tenantId, UUID employeeId, String period);

    List<WfhConsumption> findByTenantIdAndAllocationId(UUID tenantId, UUID allocationId);

    Optional<WfhConsumption> findByTenantIdAndWfhRequestIdAndReversesIdIsNull(UUID tenantId, UUID wfhRequestId);

    @Query("SELECT COALESCE(SUM(c.consumedDays), 0) FROM WfhConsumption c "
            + "WHERE c.tenantId = :tenantId AND c.allocationId = :allocationId")
    BigDecimal sumConsumedDaysByAllocation(@Param("tenantId") UUID tenantId, @Param("allocationId") UUID allocationId);

    @Query("SELECT COALESCE(SUM(c.consumedDays), 0) FROM WfhConsumption c "
            + "WHERE c.tenantId = :tenantId AND c.employeeId = :employeeId AND c.period = :period")
    BigDecimal sumConsumedDaysByPeriod(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, @Param("period") String period);
}
