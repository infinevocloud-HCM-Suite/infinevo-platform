package com.infinevo.payroll.fbp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link FbpPlan} entity (W-27.1).
 */
public interface FbpPlanRepository extends JpaRepository<FbpPlan, UUID> {

    Optional<FbpPlan> findByTenantId(UUID tenantId);
}
