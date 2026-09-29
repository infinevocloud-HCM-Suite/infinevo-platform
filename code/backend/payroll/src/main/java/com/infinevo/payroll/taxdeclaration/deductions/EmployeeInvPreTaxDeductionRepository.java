package com.infinevo.payroll.taxdeclaration.deductions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeInvPreTaxDeduction} (W-32.3).
 */
@Repository
public interface EmployeeInvPreTaxDeductionRepository extends JpaRepository<EmployeeInvPreTaxDeduction, UUID> {

    List<EmployeeInvPreTaxDeduction> findByTenantIdAndDeclarationIdOrderByCreatedAtAscIdAsc(
            UUID tenantId, UUID declarationId);

    default List<EmployeeInvPreTaxDeduction> findByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId) {
        return findByTenantIdAndDeclarationIdOrderByCreatedAtAscIdAsc(tenantId, declarationId);
    }

    void deleteByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId);
}
