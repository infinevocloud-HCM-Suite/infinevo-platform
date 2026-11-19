package com.infinevo.hrms.performance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link EmployeeGoal} (W-106.2).
 */
@Repository
public interface EmployeeGoalRepository extends JpaRepository<EmployeeGoal, UUID> {

    Optional<EmployeeGoal> findByIdAndTenantId(UUID id, UUID tenantId);

    List<EmployeeGoal> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);

    List<EmployeeGoal> findByTenantIdAndEmployeeIdInOrderByCreatedAtDesc(UUID tenantId, Collection<UUID> employeeIds);

    List<EmployeeGoal> findByTenantIdAndCycleIdOrderByCreatedAtDesc(UUID tenantId, UUID cycleId);

    List<EmployeeGoal> findByTenantIdAndEmployeeIdAndCycleIdOrderByCreatedAtDesc(
            UUID tenantId, UUID employeeId, UUID cycleId);
}
