package com.infinevo.payroll.statutory.pt;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for tenant-level professional tax override slab rows (W-31.2).
 */
public interface OrgPtOverrideSlabRepository extends JpaRepository<OrgPtOverrideSlab, UUID> {

    List<OrgPtOverrideSlab> findByTenantIdAndOverrideIdOrderBySortOrderAsc(UUID tenantId, UUID overrideId);

    void deleteByTenantIdAndOverrideId(UUID tenantId, UUID overrideId);
}
