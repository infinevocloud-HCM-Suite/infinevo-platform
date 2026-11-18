package com.infinevo.core.onboarding;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeOnboardingTaskRepository extends JpaRepository<EmployeeOnboardingTask, UUID> {

    List<EmployeeOnboardingTask> findByTenantIdAndEmployeeIdOrderByDueDateAscCreatedAtAsc(
            UUID tenantId, UUID employeeId);

    Optional<EmployeeOnboardingTask> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    long countByTenantIdAndEmployeeIdAndStatusIn(
            UUID tenantId, UUID employeeId, Collection<OnboardingTaskStatus> statuses);
}
