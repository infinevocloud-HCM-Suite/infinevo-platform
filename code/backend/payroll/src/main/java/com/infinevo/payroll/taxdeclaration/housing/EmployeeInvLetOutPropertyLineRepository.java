package com.infinevo.payroll.taxdeclaration.housing;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link EmployeeInvLetOutPropertyLine} (W-32.2).
 */
@Repository
public interface EmployeeInvLetOutPropertyLineRepository extends JpaRepository<EmployeeInvLetOutPropertyLine, UUID> {

    List<EmployeeInvLetOutPropertyLine> findByTenantIdAndPropertyIdOrderByCreatedAtAscIdAsc(
            UUID tenantId, UUID propertyId);

    default List<EmployeeInvLetOutPropertyLine> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId) {
        return findByTenantIdAndPropertyIdOrderByCreatedAtAscIdAsc(tenantId, propertyId);
    }

    List<EmployeeInvLetOutPropertyLine> findByTenantIdAndPropertyIdInOrderByCreatedAtAscIdAsc(
            UUID tenantId, Collection<UUID> propertyIds);

    default List<EmployeeInvLetOutPropertyLine> findByTenantIdAndPropertyIdIn(
            UUID tenantId, Collection<UUID> propertyIds) {
        return findByTenantIdAndPropertyIdInOrderByCreatedAtAscIdAsc(tenantId, propertyIds);
    }

    List<EmployeeInvLetOutPropertyLine> findByTenantIdAndDeclarationIdOrderByCreatedAtAscIdAsc(
            UUID tenantId, UUID declarationId);

    default List<EmployeeInvLetOutPropertyLine> findByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId) {
        return findByTenantIdAndDeclarationIdOrderByCreatedAtAscIdAsc(tenantId, declarationId);
    }

    void deleteByTenantIdAndPropertyId(UUID tenantId, UUID propertyId);

    void deleteByTenantIdAndPropertyIdIn(UUID tenantId, Collection<UUID> propertyIds);

    void deleteByTenantIdAndDeclarationId(UUID tenantId, UUID declarationId);
}
