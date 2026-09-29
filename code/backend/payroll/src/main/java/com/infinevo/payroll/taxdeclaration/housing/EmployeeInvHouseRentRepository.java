package com.infinevo.payroll.taxdeclaration.housing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeInvHouseRent} (W-32.2).
 */
@Repository
public interface EmployeeInvHouseRentRepository extends JpaRepository<EmployeeInvHouseRent, UUID> {

    List<EmployeeInvHouseRent> findByTenantIdAndDeclarationIdOrderByFromMonthAsc(UUID tenantId, UUID declarationId);

    void deleteByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId);
}
