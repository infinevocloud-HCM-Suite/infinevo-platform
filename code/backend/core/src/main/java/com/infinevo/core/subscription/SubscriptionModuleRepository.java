package com.infinevo.core.subscription;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link SubscriptionModule} (W-12.1).
 *
 * <p>Tenant isolation is enforced by PostgreSQL row-level security policy
 * on {@code core.subscription_module} for standard {@code app_user} connections.
 */
@Repository
public interface SubscriptionModuleRepository extends JpaRepository<SubscriptionModule, UUID> {

    List<SubscriptionModule> findByTenantId(UUID tenantId);

    List<SubscriptionModule> findByTenantIdAndRevokedOnIsNull(UUID tenantId);

    Optional<SubscriptionModule> findByTenantIdAndModule(UUID tenantId, PlatformModule module);
}
