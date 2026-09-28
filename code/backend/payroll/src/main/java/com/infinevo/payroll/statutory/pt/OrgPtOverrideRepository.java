package com.infinevo.payroll.statutory.pt;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for tenant-level professional tax overrides (W-31.2).
 */
public interface OrgPtOverrideRepository extends JpaRepository<OrgPtOverride, UUID> {

    Optional<OrgPtOverride> findByTenantIdAndStateCode(UUID tenantId, String stateCode);

    void deleteByTenantIdAndStateCode(UUID tenantId, String stateCode);
}
