package com.infinevo.payroll.proof;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder takes the tenant id (DEBT-022). */
public interface EmployeeProofItemDocumentRepository extends JpaRepository<EmployeeProofItemDocument, UUID> {

    List<EmployeeProofItemDocument> findByTenantIdAndItemIdInOrderByCreatedAtAscIdAsc(
            UUID tenantId, Collection<UUID> itemIds);

    Optional<EmployeeProofItemDocument> findByTenantIdAndItemIdAndDocumentId(
            UUID tenantId, UUID itemId, UUID documentId);

    long countByTenantIdAndItemId(UUID tenantId, UUID itemId);

    void deleteByTenantIdAndItemIdIn(UUID tenantId, Collection<UUID> itemIds);
}
