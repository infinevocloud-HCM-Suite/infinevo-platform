package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link LeaveMonthlyLop} (W-16.4a).
 */
public interface LeaveMonthlyLopRepository extends JpaRepository<LeaveMonthlyLop, UUID> {

    List<LeaveMonthlyLop> findByTenantIdAndEmployeeIdAndPeriodOrderByCreatedAtAsc(
            UUID tenantId, UUID employeeId, String period);

    List<LeaveMonthlyLop> findByTenantIdAndEmployeeIdOrderByPeriodAscCreatedAtAsc(UUID tenantId, UUID employeeId);

    List<LeaveMonthlyLop> findByTenantIdAndLeaveRequestId(UUID tenantId, UUID leaveRequestId);

    List<LeaveMonthlyLop> findByTenantIdAndLeaveRequestIdAndReversesIdIsNull(UUID tenantId, UUID leaveRequestId);

    @Query("SELECT COALESCE(SUM(l.lopDays), 0) FROM LeaveMonthlyLop l "
            + "WHERE l.tenantId = :tenantId AND l.employeeId = :employeeId AND l.period = :period")
    BigDecimal sumLopDaysByEmployeeAndPeriod(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, @Param("period") String period);
}
