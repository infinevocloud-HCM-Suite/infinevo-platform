package com.infinevo.payroll.taxdeclaration.deductions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeInvSection6A} (W-32.3).
 */
@Repository
public interface EmployeeInvSection6ARepository extends JpaRepository<EmployeeInvSection6A, UUID> {

    List<EmployeeInvSection6A> findByTenantIdAndDeclarationIdOrderByCreatedAtAscDescriptionAscIdAsc(
            UUID tenantId, UUID declarationId);

    default List<EmployeeInvSection6A> findByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId) {
        return findByTenantIdAndDeclarationIdOrderByCreatedAtAscDescriptionAscIdAsc(tenantId, declarationId);
    }

    void deleteByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId);
}
