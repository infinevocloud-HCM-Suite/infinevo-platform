package com.infinevo.payroll.salary;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link EmployeeBenefit} entities (W-26.2).
 */
public interface EmployeeBenefitRepository extends JpaRepository<EmployeeBenefit, UUID> {

    List<EmployeeBenefit> findAllByTenantIdAndCtcStructureId(UUID tenantId, UUID ctcStructureId);

    void deleteAllByTenantIdAndCtcStructureId(UUID tenantId, UUID ctcStructureId);
}
