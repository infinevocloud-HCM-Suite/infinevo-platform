package com.infinevo.hrms.performance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for employee goal check-in history records (W-106.2).
 */
@Repository
public interface EmployeeGoalCheckInRepository extends JpaRepository<EmployeeGoalCheckIn, UUID> {

    List<EmployeeGoalCheckIn> findByTenantIdAndGoalIdOrderByCreatedAtDesc(UUID tenantId, UUID goalId);
}
