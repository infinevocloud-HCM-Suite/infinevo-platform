package com.infinevo.payroll.salary;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link EmployeeReimbursement} entities (W-26.2).
 */
public interface EmployeeReimbursementRepository extends JpaRepository<EmployeeReimbursement, UUID> {

    List<EmployeeReimbursement> findAllByTenantIdAndCtcStructureId(UUID tenantId, UUID ctcStructureId);

    void deleteAllByTenantIdAndCtcStructureId(UUID tenantId, UUID ctcStructureId);
}
