package com.infinevo.core.setup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TenantSetupStep} (W-24.1).
 */
@Repository
public interface TenantSetupStepRepository extends JpaRepository<TenantSetupStep, UUID> {

    List<TenantSetupStep> findByTenantIdOrderByDisplayOrderAsc(UUID tenantId);

    Optional<TenantSetupStep> findByTenantIdAndStepCode(UUID tenantId, String stepCode);
}
