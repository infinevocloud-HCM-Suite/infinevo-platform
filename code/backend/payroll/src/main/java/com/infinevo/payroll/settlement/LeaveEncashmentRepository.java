package com.infinevo.payroll.settlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LeaveEncashmentSettlement} (W-90.1).
 */
@Repository
public interface LeaveEncashmentRepository extends JpaRepository<LeaveEncashmentSettlement, UUID> {

    List<LeaveEncashmentSettlement> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<LeaveEncashmentSettlement> findByTenantIdAndEmployeeIdAndStatusNot(
            UUID tenantId, UUID employeeId, String status);

    Optional<LeaveEncashmentSettlement> findByTenantIdAndEmployeeIdAndLeaveTypeIdAndStatusNot(
            UUID tenantId, UUID employeeId, UUID leaveTypeId, String status);
}
