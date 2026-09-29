package com.infinevo.payroll.taxdeclaration.housing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeInvLetOutProperty} (W-32.2).
 */
@Repository
public interface EmployeeInvLetOutPropertyRepository extends JpaRepository<EmployeeInvLetOutProperty, UUID> {

    List<EmployeeInvLetOutProperty> findByTenantIdAndDeclarationIdOrderByCreatedAtAscIdAsc(
            UUID tenantId, UUID declarationId);

    default List<EmployeeInvLetOutProperty> findByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId) {
        return findByTenantIdAndDeclarationIdOrderByCreatedAtAscIdAsc(tenantId, declarationId);
    }

    void deleteByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId);
}
