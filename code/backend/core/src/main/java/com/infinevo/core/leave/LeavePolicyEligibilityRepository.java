package com.infinevo.core.leave;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link LeavePolicyEligibility} (W-16.1).
 */
public interface LeavePolicyEligibilityRepository extends JpaRepository<LeavePolicyEligibility, UUID> {

    List<LeavePolicyEligibility> findByTenantIdAndPolicyId(UUID tenantId, UUID policyId);

    void deleteByTenantIdAndPolicyId(UUID tenantId, UUID policyId);
}
