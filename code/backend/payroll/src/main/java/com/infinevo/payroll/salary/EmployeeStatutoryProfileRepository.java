package com.infinevo.payroll.salary;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link EmployeeStatutoryProfile} entities (W-26.2).
 */
public interface EmployeeStatutoryProfileRepository extends JpaRepository<EmployeeStatutoryProfile, UUID> {

    Optional<EmployeeStatutoryProfile> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    boolean existsByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
}
