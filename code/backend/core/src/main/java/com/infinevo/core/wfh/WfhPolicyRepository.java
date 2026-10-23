package com.infinevo.core.wfh;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link WfhPolicy} entities (W-74).
 */
@Repository
public interface WfhPolicyRepository extends JpaRepository<WfhPolicy, UUID> {

    Optional<WfhPolicy> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<WfhPolicy> findFirstByTenantIdAndActiveTrueOrderByEffectiveFromDesc(UUID tenantId);

    List<WfhPolicy> findByTenantIdOrderByEffectiveFromDesc(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
