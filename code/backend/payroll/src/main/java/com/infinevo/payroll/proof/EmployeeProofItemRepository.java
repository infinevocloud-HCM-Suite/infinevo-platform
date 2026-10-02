package com.infinevo.payroll.proof;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder takes the tenant id (DEBT-022). */
public interface EmployeeProofItemRepository extends JpaRepository<EmployeeProofItem, UUID> {

    List<EmployeeProofItem> findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(UUID tenantId, UUID proofId);

    Optional<EmployeeProofItem> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<EmployeeProofItem> findByTenantIdAndProofIdAndId(UUID tenantId, UUID proofId, UUID id);

    void deleteByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
