package com.infinevo.payroll.taxdeclaration.summary;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeInvOtherIncome} (W-32.4).
 */
@Repository
public interface EmployeeInvOtherIncomeRepository extends JpaRepository<EmployeeInvOtherIncome, UUID> {

    List<EmployeeInvOtherIncome> findByTenantIdAndDeclarationIdOrderByCreatedAtAscIdAsc(
            UUID tenantId, UUID declarationId);

    default List<EmployeeInvOtherIncome> findByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId) {
        return findByTenantIdAndDeclarationIdOrderByCreatedAtAscIdAsc(tenantId, declarationId);
    }

    void deleteByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId);
}
