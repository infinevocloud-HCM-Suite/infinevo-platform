package com.infinevo.payroll.salary;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link EmployeeEarning} entities (W-26.2).
 */
public interface EmployeeEarningRepository extends JpaRepository<EmployeeEarning, UUID> {

    List<EmployeeEarning> findAllByTenantIdAndCtcStructureId(UUID tenantId, UUID ctcStructureId);

    void deleteAllByTenantIdAndCtcStructureId(UUID tenantId, UUID ctcStructureId);
}
