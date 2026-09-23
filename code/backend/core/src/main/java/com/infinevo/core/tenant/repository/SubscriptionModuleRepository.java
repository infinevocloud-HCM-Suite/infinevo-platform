package com.infinevo.core.tenant.repository;

import com.infinevo.core.tenant.entity.SubscriptionModule;
import com.infinevo.shared.entitlement.ModuleCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionModuleRepository extends JpaRepository<SubscriptionModule, UUID> {
    List<SubscriptionModule> findByTenantIdAndStatus(UUID tenantId, String status);

    boolean existsByTenantIdAndModuleCodeAndStatus(UUID tenantId, ModuleCode moduleCode, String status);
}
